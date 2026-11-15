# Task 10 — Integration & E2E acceptance

Date: 2026-11-15. Source requirements: `Nhom2_CNPM 1.pdf`, chapter 4 (UC01–UC10, pages 25–63). The PRD was supplied outside the repository and is not redistributed here.

## Test environment and scope

The browser suite starts the packaged Spring Boot backend, the Vite frontend, and a real isolated MySQL 8.4 database named `voicemap_task10_<random hex>`. It inserts an isolated Admin account and POIs/routes, then drops only that schema during teardown. It does not access the demo database. Playwright drives Chromium in CI and Edge locally. Browser geolocation is simulated. Translation, Google Standard TTS and OSRM Directions are deterministic local HTTP providers. The TTS provider returns a valid WAV file; browser playback, HTTP APIs, pipeline state, Flyway migrations, MySQL persistence and Monitoring queries are real.

The suite is `voiceover-frontend/e2e/task10.e2e.mjs`; run it with `E2E_MYSQL_PASSWORD`, optional `E2E_MYSQL_PORT` and `npm run test:e2e`. The CI job provisions its own MySQL service and installs Chromium. The regular frontend unit-test discovery does not run the E2E file.

## UC acceptance matrix

| UC | Status | Evidence and scope |
| --- | --- | --- |
| UC01 GPS and POI map | PASS | Browser receives simulated reliable GPS; published POIs appear on the map. Denied and weak GPS show recovery/status messages. |
| UC02 multilingual narration | PASS | Pipeline produces vi/en audio; the guest browser starts valid vi WAV playback and MySQL records `AUTO_PLAY` only after start. Missing en file falls back to vi through the real guide API. Live external voices were not used. |
| UC03 personal route | PASS | Guest opens a suggested route, customizes and confirms it; confirmation alone does not create `ROUTE_SELECTED`. |
| UC04 navigation | PASS | Guest starts a route with simulated GPS; `ROUTE_SELECTED` is recorded, one stop becomes VISITED, another is SKIPPED, and the journey completes. Directions is a local provider. |
| UC05 Admin login | PASS | Admin signs in through the browser; unauthenticated Monitoring API returns 401. |
| UC06 POI management | PASS | Admin creates a draft POI in the browser, waits for content and publishes it; a second POI is created and published through the authenticated Admin API. MySQL status is checked. |
| UC07 translation/TTS pipeline | PASS | vi/en tasks and audio complete in MySQL. Forced TTS failure produces a FAILED task visible in review; explicit retry completes and restores audio. Providers are local fakes. |
| UC08 content review | PASS | Admin reviews generated English content, manually edits a separate draft translation, and verifies it becomes STALE when the source changes. |
| UC09 Admin routes | PASS | Admin API creates a published route, the management screen displays it, and guest navigation consumes it. |
| UC10 Monitoring | PASS | Dashboard audio and route counters are compared with MySQL `usage_event` rows within 10 seconds. A mocked 503 response shows an error and recovery; unauthorized API access is rejected. |

## Main flows and error coverage

- **A — Admin content:** browser login → draft POI → vi/en pipeline → review → publish → guest map/audio. PASS.
- **B — Guest visit:** map/GPS → POI → personal route confirmation → start → audio → VISITED/SKIPPED → complete. PASS.
- **C — Monitoring:** actual browser playback and route-start events → MySQL rows → Dashboard counters within 10 seconds. PASS. In the isolated run the dashboard shows one automatic listen and one route start, matching one `AUTO_PLAY` and one `ROUTE_SELECTED` row.
- **Errors tested:** denied/weak GPS, GPS fix expiry after signal loss and recovery on a new fix, blocked autoplay without false listen count, failed POI API request and retry, invalid localStorage, TTS failure/retry, source edit causing STALE translation, Directions 503, missing selected-language audio fallback, Dashboard 503 and recovery, unauthenticated Monitoring 401. Browser GPS and Dashboard 503 are simulated; MySQL itself was not stopped.
- **Responsive:** 320px, 390px, 768px guest map; no document horizontal overflow.

## Automated test results

| Suite | Result | Notes |
| --- | --- | --- |
| Backend `mvnw verify` | 147 tests, 0 fail, 18 skipped | The skipped cases are opt-in live Edge TTS and MySQL tests; MySQL tests ran separately below. |
| Frontend `node --test src/*.test.js` | 69 pass, 0 fail, 0 skipped | Vite production build also passed. |
| Python worker `unittest` | 7 pass | Worker dependencies installed in a local virtual environment. |
| MySQL integration | 16 pass, 0 fail, 0 skipped | Four opt-in integration classes executed on isolated MySQL 8.4. |
| Playwright browser + MySQL | 10 pass, 0 fail, 0 skipped | Includes the parent test and nine named subtests, including GPS signal loss/recovery. |

Raw logs from the original local run: `docs/task10-evidence/logs/`. Browser screenshots: `docs/task10-evidence/`. The updated CI run on commit `b192cdf` passed [all five jobs](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43708136351), including the MySQL integration report check and the new E2E case. This report/workflow follow-up commit requires its own CI run; use the latest successful check on [PR #36](https://github.com/BTLoc2905/voiceover-backend/pull/36) as final release evidence. CI now uploads screenshots as an artifact on both success and failure.

## Bugs and limitations

No application defect of high or critical severity was reproduced in the three main flows. The initial E2E run exposed only test locator/stale-page issues; those were corrected in the test. No production code was changed for those issues. The browser coverage gap for GPS signal loss was closed in PR #36 (test-only, low severity).

NOT TESTED on a physical phone: outdoor GPS quality, actual walking distance/ETA, mobile browser audio policy and signal loss. NOT TESTED in this E2E suite against external Google Translation, real TTS or OSRM services; local HTTP providers were used for deterministic integration. A separate [Staging CD smoke run](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43706479962) exercised live Edge TTS, but does not replace the full content pipeline E2E. A real MySQL outage and off-route recovery were not exercised in this browser suite; API error handling and off-route logic have separate automated coverage. These limits must be cleared before claiming field acceptance.
