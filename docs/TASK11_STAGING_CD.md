# Task 11 — Staging CD runbook and acceptance

Date: 2026-11-15. The branch starts from `develop` at `9aa6446`, which includes PR #26 and Task 10. No Staging host or GitHub `staging` Environment was provided for this task. **Actual Staging deployment, live health checks and rollback are NOT TESTED.** The PR validation job uses a disposable Docker/MySQL stack; its results must not be presented as a real deployment.

## Docker audit

| Component | Container or provider | Persistent state |
| --- | --- | --- |
| Backend and pipeline worker | `Dockerfile`, Java 21 with Edge TTS Python worker in the same container | `audio_data` at `/app/storage/audio`; `image_data` at `/app/storage/images` |
| Frontend and Admin Dashboard | `voiceover-frontend/Dockerfile`, Nginx proxy to Backend | None; rebuildable image |
| MySQL 8.4 | `compose.yaml` | `mysql_data` at `/var/lib/mysql` |
| Translation, Edge TTS, walking route | External services called from Backend | Audio output remains in `audio_data`; no local routing container |

`compose.staging.yaml` substitutes the two application images with full `sha-<40-character commit>` GHCR tags. The underlying Compose file keeps the named MySQL, Audio and image volumes. Staging uses `docker compose up --no-build --wait`; it does not call `down -v` or delete the demo database. The older `publish-image` job in `maven.yml` is a separate main-branch backend publisher and is not the Staging release source.

## One-time server setup

1. Prepare a dedicated Staging Linux host with Docker Engine, Compose v2, Python 3, Bash, `curl`, `jq`, SSH and outbound access to GHCR, Edge TTS, Translation and routing. Give the deployment user permission to run Docker. Allow inbound SSH from the Actions runner and expose the frontend through a TLS reverse proxy or VPN.
2. Create `/opt/voicemap`, owned by the deployment user. Copy `.env.example` to `/opt/voicemap/.env` locally on the server. Set unique `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD`, `FRONTEND_BIND=127.0.0.1`, `FRONTEND_PORT=8088`, `TTS_PROVIDER=edge`, and any service keys. Run `chmod 600 /opt/voicemap/.env`. Do not upload this file to Git.
3. Create `/opt/voicemap/.env.smoke` with Bash-quoted `STAGING_ADMIN_EMAIL` and `STAGING_ADMIN_PASSWORD` for a dedicated ACTIVE Admin account in the Staging database; run `chmod 600`. The full smoke script logs in to Monitoring without printing credentials. **A fresh empty database needs an approved one-time Admin bootstrap after Flyway initializes it.** This workflow does not create an Admin account; do not approve its first deployment until the group has planned that bootstrap, or the Monitoring smoke will fail.
4. Log in to GHCR **on the server** using a read-only `read:packages` credential via `docker login ghcr.io --username <account> --password-stdin`. Keep that credential in the server's Docker credential store. The workflow does not transmit a GHCR token to the server.
5. In GitHub Settings → Environments, configure `staging` with **required reviewers**, **prevent self review**, and deployment branches restricted to `develop`. Configure environment secrets `STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY` and `STAGING_HOST_KEY` (the pinned OpenSSH known-hosts line for the host). The workflow refuses release if these protection rules or the exact commit's push CI are absent.
6. The repository's default branch is `main`. After this Task 11 PR is merged into `develop`, promote the reviewed workflow to `main` through a **separate reviewed PR**. GitHub requires a `workflow_dispatch` workflow file on the default branch before its manual Run workflow button becomes available. Then select `develop` in the branch dropdown; the release gate still verifies that branch's exact push CI. Do not push directly to either branch. See [GitHub's manual workflow rules](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

The frontend's `FRONTEND_BIND=127.0.0.1` keeps port 8088 local to the server; put TLS or VPN in front of it. Backend and MySQL have no host-published ports. Restrict SSH and the server's `.env` files to trusted operators.

## Run a release

1. Merge the reviewed PR into `develop`, promote the workflow to default branch `main` by reviewed PR, then wait for **VoiceMap CI** on the exact `develop` SHA to pass `backend-test`, `frontend-test`, `python-test`, `mysql-integration-test` and `e2e-test`.
2. Open **Actions → VoiceMap Staging CD → Run workflow**, select `develop`. The release gate checks the exact dispatch SHA and CI job conclusions. The workflow builds and publishes Backend and Frontend images to GHCR using the full SHA tag.
3. A required reviewer approves the `staging` deployment. The workflow uses pinned-host-key SSH, copies only reviewed Compose/script files, and invokes `/opt/voicemap/staging.py deploy` on the host. It does not copy `.env` or `.env.smoke`.
4. The server records prior image refs, backs up MySQL to `/opt/voicemap/backups/`, pulls the two SHA images, starts the stack without rebuilding, waits for healthy containers and runs `smoke.sh full`.

Only run this workflow after the group authorizes deployment to the named Staging host. There is no automatic deploy on merge and no production deploy path in this PR.

## Post-deploy checks and logs

The full smoke script checks: all three container health statuses, frontend HTML, public language and POI APIs, MySQL query, Flyway history with no failed migration, pipeline table, authenticated Monitoring counters, and a real short Edge TTS generation in a temporary container file. It does not create or delete demo POIs. It does not prove a complete translation pipeline job; that remains a manual Staging check with a dedicated test POI.

On the server, inspect:

```bash
cd /opt/voicemap
docker compose --env-file .env -f compose.yaml -f compose.staging.yaml ps
docker compose --env-file .env -f compose.yaml -f compose.staging.yaml logs --tail=200 backend frontend mysql
cat releases/current.json
```

The PR validation job separately builds both Docker images, starts disposable MySQL/Backend/Frontend and runs `smoke.sh basic`. It checks HTTP, DB, Flyway and container health without Staging secrets or live Edge TTS. [Staging CD validation run 43684363022](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43684363022) passed: 13 policy/rollback tests; MySQL, Backend and Frontend healthy; Frontend HTTP and public API reachable; MySQL/Flyway rank 20 and pipeline table checked. [Regular VoiceMap CI run 43684362992](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43684362992) passed all five required test jobs on the same initial Task 11 commit `7a18562`.

The local development machine for this task has no Docker CLI, so Docker build and container startup are verified by the PR job rather than claimed as local results.

## Backup and rollback

Each update with an existing MySQL container writes a mode-0600 `mysqldump` before rollout. `releases/current.json` stores the previous Backend/Frontend SHA refs and the backup path. If deploy or smoke fails, `releases/pending.json` preserves the same information; the script does **not** automatically alter MySQL or attempt image rollback. Copy database and Audio/image backups off-host before a risky migration.

To back up named file volumes separately, use read-only mounts and a protected backup directory:

```bash
cd /opt/voicemap
mkdir -p backups
chmod 700 backups
docker run --rm -v voicemap_audio_data:/data:ro -v "$PWD/backups":/backup busybox:1.36 tar -C /data -czf /backup/audio.tar.gz .
docker run --rm -v voicemap_image_data:/data:ro -v "$PWD/backups":/backup busybox:1.36 tar -C /data -czf /backup/images.tar.gz .
chmod 600 backups/*.tar.gz
```

If a release fails and Flyway's installed rank is unchanged, an operator can run:

```bash
python3 /opt/voicemap/staging.py rollback
```

This restores the exact previous SHA image refs, waits for health and re-runs the full smoke test. It keeps MySQL, Audio and image volumes. If the Flyway rank changed or cannot be read, rollback refuses to run. The team must inspect the migration and the saved backup, then plan a manual compatible fix or database restore on Staging. Never run `docker compose down -v`; never automatically undo a database migration.

## Acceptance status

Changed files: `.github/workflows/deploy.yml`, `compose.staging.yaml`, `compose.yaml`, `.env.example`, `scripts/deploy/verify_release.py`, `scripts/deploy/staging.py`, `scripts/deploy/smoke.sh`, their Python tests, this runbook and the implementation plan. No existing test file was removed.

| Check | Status |
| --- | --- |
| PR #26 included in source; separate Task 11 branch | PASS |
| Release gate, SHA image/rollback policy unit tests | PASS — 13/13 locally and in PR validation |
| Disposable Docker build, Compose and basic smoke | PASS — GitHub Actions run 43684363022, Flyway rank 20 |
| Manual `workflow_dispatch` availability | NOT TESTED — workflow must first be promoted to default branch `main` through review |
| Real Staging deployment, full smoke, backup and rollback rehearsal | NOT TESTED — no approved Staging host/configuration supplied |
| Production deployment | NOT TESTED — outside Task 11 Staging workflow |

Remaining deployment gaps: the group must promote the workflow to default branch `main` through review and provide a Staging host, protected GitHub Environment, SSH secrets, server `.env` files and an Admin bootstrap for a fresh database. Live translation pipeline, live TTS, backup restoration and rollback rehearsal require that approved environment. No high-severity application issue was found in the disposable Compose smoke.
