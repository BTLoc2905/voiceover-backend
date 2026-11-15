"""Deploy SHA-tagged VoiceMap images without modifying persistent volumes."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone


IMAGE = re.compile(r"^ghcr\.io/[a-z0-9._-]+/voicemap-(backend|frontend):sha-([0-9a-f]{40})$")
COMPOSE = ("docker", "compose", "--env-file", ".env", "-f", "compose.yaml", "-f", "compose.staging.yaml")
RANK_QUERY = ('MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=TCP -h 127.0.0.1 '
              '-u "$MYSQL_USER" "$MYSQL_DATABASE" -Nse '
              '"SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history"')
DUMP_QUERY = ('MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --protocol=TCP -h 127.0.0.1 '
              '-u "$MYSQL_USER" --single-transaction --routines --triggers --events '
              '--set-gtid-purged=OFF "$MYSQL_DATABASE"')


def checked_images(backend, frontend):
    a, b = IMAGE.fullmatch(backend or ""), IMAGE.fullmatch(frontend or "")
    if not a or a.group(1) != "backend" or not b or b.group(1) != "frontend":
        raise ValueError("Backend and Frontend must use GHCR SHA release images")
    if a.group(2) != b.group(2) or backend.split("/")[1] != frontend.split("/")[1]:
        raise ValueError("Backend and Frontend must come from the same commit and owner")
    return a.group(2)


def save_state(path, state):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    temp.chmod(0o600)
    temp.replace(path)


def deploy(ops, root, backend, frontend):
    sha = checked_images(backend, frontend)
    ops.preflight()
    old_backend, old_frontend = ops.current_images()
    before_rank = ops.database_rank()
    if (old_backend or old_frontend) and before_rank is None:
        raise RuntimeError("Existing application has no readable Flyway history; refusing rollout")

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup = None
    if before_rank is not None:
        backup = root / "backups" / f"mysql-{timestamp}-{sha[:12]}.sql"
        backup.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        ops.backup_database(backup)
    state = {
        "sha": sha, "backend": backend, "frontend": frontend,
        "previous_backend": old_backend, "previous_frontend": old_frontend,
        "pre_migration_rank": before_rank, "backup_path": str(backup) if backup else None,
        "started_at": datetime.now(timezone.utc).isoformat(), "status": "pending",
    }
    pending = root / "releases/pending.json"
    save_state(pending, state)
    ops.pull_and_up(backend, frontend)
    ops.smoke()
    state["status"] = "healthy"
    state["completed_at"] = datetime.now(timezone.utc).isoformat()
    save_state(root / "releases/current.json", state)
    pending.unlink()
    return state


def rollback(ops, root):
    state_path = root / "releases/pending.json"
    if not state_path.exists():
        state_path = root / "releases/current.json"
    if not state_path.exists():
        raise RuntimeError("No release record exists")
    state = json.loads(state_path.read_text(encoding="utf-8"))
    backend, frontend = state.get("previous_backend"), state.get("previous_frontend")
    if not backend or not frontend:
        raise RuntimeError("No previous SHA images exist for rollback")
    checked_images(backend, frontend)
    ops.preflight()
    current_rank = ops.database_rank()
    if current_rank is None or current_rank != state["pre_migration_rank"]:
        raise RuntimeError("Flyway history changed or cannot be read; review database backup before manual recovery")
    ops.pull_and_up(backend, frontend)
    ops.smoke()
    previous = (state["backend"], state["frontend"])
    state.update({"backend": backend, "frontend": frontend, "previous_backend": previous[0],
                  "previous_frontend": previous[1], "status": "rolled_back",
                  "completed_at": datetime.now(timezone.utc).isoformat()})
    save_state(root / "releases/current.json", state)
    (root / "releases/pending.json").unlink(missing_ok=True)
    return state


class DockerOps:
    def __init__(self, root, backend, frontend):
        self.root = root
        self.env = {**os.environ, "BACKEND_IMAGE": backend, "FRONTEND_IMAGE": frontend}

    def run(self, args, *, stdout=None):
        return subprocess.run(args, cwd=self.root, env=self.env, text=stdout is None,
                              stdout=stdout if stdout is not None else subprocess.PIPE,
                              stderr=subprocess.PIPE, check=True)

    def compose(self, *args, stdout=None):
        return self.run((*COMPOSE, *args), stdout=stdout)

    def preflight(self):
        for name in (".env", ".env.smoke", "compose.yaml", "compose.staging.yaml", "smoke.sh"):
            path = self.root / name
            if not path.is_file() or path.is_symlink():
                raise RuntimeError(f"Missing or unsafe server file: {name}")
        for name in (".env", ".env.smoke"):
            if self.root.joinpath(name).stat().st_mode & 0o077:
                raise RuntimeError(f"Server secret file {name} must be mode 0600")
        self.compose("config", "--quiet")

    def container(self, service):
        return self.compose("ps", "-q", service).stdout.strip()

    def current_images(self):
        ids = [self.container(name) for name in ("backend", "frontend")]
        if bool(ids[0]) != bool(ids[1]):
            raise RuntimeError("Only one application container is running; inspect Staging manually")
        if not ids[0]:
            return None, None
        return tuple(self.run(("docker", "inspect", "--format", "{{.Config.Image}}", item)).stdout.strip()
                     for item in ids)

    def database_rank(self):
        if not self.container("mysql"):
            return None
        return int(self.compose("exec", "-T", "mysql", "sh", "-ec", RANK_QUERY).stdout.strip())

    def backup_database(self, target):
        try:
            with target.open("wb") as output:
                target.chmod(0o600)
                self.compose("exec", "-T", "mysql", "sh", "-ec", DUMP_QUERY, stdout=output)
            if target.stat().st_size == 0:
                raise RuntimeError("MySQL backup is empty")
        except Exception:
            target.unlink(missing_ok=True)
            raise

    def pull_and_up(self, backend, frontend):
        self.env.update(BACKEND_IMAGE=backend, FRONTEND_IMAGE=frontend)
        self.compose("pull", "backend", "frontend")
        self.compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "240")

    def smoke(self):
        subprocess.run(("bash", "smoke.sh", "full"), cwd=self.root, env=self.env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("deploy", "rollback"))
    parser.add_argument("--backend-image")
    parser.add_argument("--frontend-image")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    os.umask(0o077)
    import fcntl
    with (root / "deploy.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            if args.action == "deploy":
                if not args.backend_image or not args.frontend_image:
                    parser.error("deploy requires both SHA image refs")
                state = deploy(DockerOps(root, args.backend_image, args.frontend_image), root,
                               args.backend_image, args.frontend_image)
            else:
                state_file = root / "releases/pending.json"
                if not state_file.exists():
                    state_file = root / "releases/current.json"
                if not state_file.exists():
                    raise RuntimeError("No release record exists")
                current = json.loads(state_file.read_text(encoding="utf-8"))
                state = rollback(DockerOps(root, current["backend"], current["frontend"]), root)
        except (RuntimeError, ValueError, subprocess.CalledProcessError) as error:
            print(f"Staging {args.action} failed: {error}", file=sys.stderr)
            return 1
    print(f"Staging {state['status']}: {state['backend']} / {state['frontend']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
