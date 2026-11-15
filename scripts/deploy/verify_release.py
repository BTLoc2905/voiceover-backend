"""Fail closed unless Staging targets only develop and exact push CI passed."""

import json
import os
import re
import sys
import urllib.request


REQUIRED_JOBS = frozenset({
    "backend-test", "frontend-test", "python-test", "mysql-integration-test", "e2e-test",
})


def verify_release(get_json, repository, sha, git_ref):
    if git_ref != "refs/heads/develop":
        raise ValueError("Staging dispatch must run from develop")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("A full lowercase commit SHA is required")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid GitHub repository")

    base = f"repos/{repository}"
    environment = get_json(f"{base}/environments/staging")
    policy = environment.get("deployment_branch_policy") or {}
    if policy.get("custom_branch_policies") is not True or policy.get("protected_branches") is not False:
        raise ValueError("staging must use a custom develop branch rule")
    rules = get_json(f"{base}/environments/staging/deployment-branch-policies?per_page=100")
    if rules.get("total_count") != 1 or [rule.get("name") for rule in rules.get("branch_policies", [])] != ["develop"]:
        raise ValueError("staging must allow only develop")

    runs = get_json(f"{base}/actions/workflows/maven.yml/runs?head_sha={sha}&event=push&per_page=100")
    for run in runs.get("workflow_runs", []):
        if (run.get("head_sha") != sha or run.get("head_branch") != "develop"
                or run.get("event") != "push" or run.get("status") != "completed"
                or run.get("conclusion") != "success"):
            continue
        jobs = get_json(f"{base}/actions/runs/{run['id']}/jobs?per_page=100").get("jobs", [])
        passed = {job.get("name") for job in jobs if job.get("conclusion") == "success"}
        missing = REQUIRED_JOBS - passed
        if not missing:
            return run["html_url"]
        raise ValueError(f"CI run for exact commit lacks passing jobs: {', '.join(sorted(missing))}")
    raise ValueError("No successful push CI run exists for the exact commit on develop")


def main():
    token = os.environ["GITHUB_TOKEN"]
    repository = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ["GITHUB_SHA"]
    git_ref = os.environ["GITHUB_REF"]

    def get_json(path):
        request = urllib.request.Request(
            f"https://api.github.com/{path}",
            headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                     "User-Agent": "VoiceMap-CD"},
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    try:
        url = verify_release(get_json, repository, sha, git_ref)
    except (ValueError, OSError) as error:
        print(f"Release gate denied: {error}", file=sys.stderr)
        return 1
    print(f"Verified push CI for {sha}: {url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
