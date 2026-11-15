# Task 11 Staging CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a reviewable, gated Staging deployment path using GitHub Actions, GHCR and Docker Compose without touching a real server until the group configures and approves it.

**Architecture:** Manual dispatch from `develop` verifies the exact commit's successful push CI and a protected `staging` environment. It builds SHA-tagged Backend and Frontend images, then an approved job transfers Compose and deploy scripts over pinned-host-key SSH. The server keeps MySQL, audio and image named volumes, takes a database backup, checks health and smoke tests, and retains prior image references for rollback. A PR validation job exercises Docker build, Compose and basic health without Staging secrets.

**Tech Stack:** GitHub Actions, Docker Buildx/GHCR, Docker Compose, Bash, Python 3 unittest.

**Spec:** User's TASK 11 request in this conversation; existing `compose.yaml`, Dockerfiles and `.github/workflows/maven.yml`.

## Global constraints

- Start from latest `develop`; PR #26 must be included. Work on a separate branch and open a PR to `develop`.
- No direct push to `main`/`develop`; no real-server deployment without group permission.
- No committed secrets or `.env`; no deletion of demo data or test files; never use `docker compose down -v`.
- Distinguish a passing disposable CI Compose smoke from an actual Staging deployment.

## Review focus

- Unapproved `staging` environment: release gate refuses to publish/deploy.
- CI success on a different SHA or PR event: release gate rejects it.
- Failed rollout with Flyway schema change: preserve backup and stop automatic image rollback.
- Failed rollout without schema change: restore previous image refs and re-run smoke.
- Missing server volumes or credentials: fail before changing the running application.

---

### Task 1: Release gate and image contract

**Files:** Create `scripts/deploy/verify_release.py`, `scripts/deploy/test_verify_release.py`; create `compose.staging.yaml`.

- [ ] Write tests for exact SHA, push CI success, required CI jobs, and staging required reviewers; verify failures.
- [ ] Implement the GitHub API release gate and SHA-tag/image validation; verify tests pass.
- [ ] Define staging Compose image overrides while retaining base volumes and health checks; validate merged config on CI.

### Task 2: Server deployment, smoke and rollback

**Files:** Create `scripts/deploy/staging.sh`, `scripts/deploy/smoke.sh`, `scripts/deploy/test_staging.py`.

- [ ] Write failure-path tests for no prior image, backup failure, unchanged-schema rollback, changed-schema manual recovery; verify failures.
- [ ] Implement preflight, DB backup, prior-image snapshot, `compose pull/up --no-build --wait`, smoke and rollback state.
- [ ] Implement frontend/API/MySQL/Flyway/container/TTS/Admin Monitoring checks, with a basic mode for disposable CI validation.
- [ ] Run script tests and shell syntax checks.

### Task 3: CD workflow and deployment guide

**Files:** Create `.github/workflows/deploy.yml`, `docs/TASK11_STAGING_CD.md`; optionally adjust `.env.example` only for nonsecret documentation.

- [ ] Add PR validation job that builds Backend/Frontend containers and checks Compose/basic smoke on a disposable runner.
- [ ] Add manual release gate, SHA-tagged GHCR build, and protected Staging SSH deployment jobs; no automatic server deploy.
- [ ] Document Docker/server setup, GitHub Environments/Secrets, launch, logs, backup, rollback and limits.
- [ ] Run local unit/syntax checks, open PR to `develop`, and follow PR CI; report Staging as NOT TESTED if no server is configured.
