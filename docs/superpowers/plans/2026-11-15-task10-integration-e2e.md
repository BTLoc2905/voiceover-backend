# Task 10 Integration and E2E Test Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Verify VoiceMap UC01–UC10 across the browser, backend and isolated MySQL, with reproducible CI evidence and an explicit field-test boundary.

**Architecture:** A Playwright browser suite starts the application against a disposable MySQL schema. Translation and TTS use a local deterministic HTTP provider that returns valid text and WAV; the application, persistence, session auth, routing logic and browser playback remain real. The suite separates provider-mock results from real services in its report.

**Tech Stack:** Spring Boot 4/JDK 21, MySQL 8.4, Vite/React, Node 22, Playwright Chromium, GitHub Actions.

**Spec:** User Task 10 request and local `C:\Users\thanh\Downloads\Nhom2_CNPM 1.pdf`, especially F-01–F-06 and UC01–UC10.

## Global Constraints

- Use only a uniquely named test schema and a temporary audio directory; never mutate the demo schema.
- Preserve all existing backend, frontend, Python and MySQL tests.
- Label mocked translation/TTS, geolocation and Directions separately from actual browser/backend/MySQL behavior.
- Report physical-device GPS and real external providers as NOT TESTED unless exercised.

## Review Focus

- API or database outage during a browser journey must show a recoverable error.
- Stale or denied GPS must never trigger auto playback or false arrival.
- A route view must not count as a route start.
- Audio preload or completion must not count as a new listen.
- A failed provider must leave an observable failed task and permit an explicit retry.

### Task 1: Isolated test environment and baseline

**Files:** Create `voiceover-frontend/e2e/harness.mjs`; modify `voiceover-frontend/package.json`; test through `npm run test:e2e` and existing suites.

- [ ] Create a random MySQL schema, start a deterministic translation/TTS provider, backend and frontend, and seed only test Admin credentials.
- [ ] Run baseline backend, frontend, Python and MySQL integration suites; save logs outside the repo.
- [ ] Tear down processes and the random schema after the run, even on failure.

### Task 2: Three browser journeys

**Files:** Create `voiceover-frontend/e2e/task10.spec.mjs` and fixture helpers as needed.

- [ ] Write failing E2E assertions for Admin login → POI draft → pipeline completion → review → publication → guest audio.
- [ ] Write failing E2E assertions for guest GPS/map → Route preview/start → directions → audio → VISITED/SKIPPED → complete.
- [ ] Write failing E2E assertions for guest events → MySQL rows → Monitoring counters and ≤10-second refresh.
- [ ] Run against isolated MySQL and fix any verified application bug with a failing test first.

### Task 3: Error and responsive branches

**Files:** Extend `voiceover-frontend/e2e/task10.spec.mjs`.

- [ ] Exercise denied/weak/lost GPS, API/MySQL outage, Directions error or deviation, missing audio, autoplay denial, provider failure/retry, manual translation STALE, invalid localStorage and unauthenticated Admin API.
- [ ] Verify 320, 390 and 768 px screenshots and horizontal overflow.
- [ ] Keep any untested external-service or device behavior marked NOT TESTED.

### Task 4: CI and acceptance

**Files:** Modify `.github/workflows/maven.yml`; create `docs/TASK10_INTEGRATION_E2E.md`; save small screenshots under `docs/task10-evidence/`.

- [ ] Add an E2E job with MySQL and Chromium to CI; run all existing suites and E2E locally.
- [ ] Record UC01–UC10 PASS/FAIL/NOT TESTED, test counts, defects and fixed commits.
- [ ] Open a PR to `develop`, observe CI on the PR head, and confirm no critical defect remains in the three journeys.
