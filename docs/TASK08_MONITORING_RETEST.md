# Task 08 — Monitoring Dashboard retest

Date: 2026-11-15. Test stack: MySQL Community Server 8.4.13 on `127.0.0.1:33306`, isolated schema `voicemap_task08` (Flyway v1–v20), backend on `localhost:8080`, Vite frontend on `localhost:5173`. No existing database was modified. The downloaded MySQL ZIP matched the MD5 on Oracle's release page.

## Acceptance status

**Partially accepted.** Core Dashboard data, Admin access, and DB-outage handling were verified live. Actual GPS-triggered autoplay, a Route start through the guest UI, browser Network-panel evidence, and a repeated or load-tested ≤10-second SLA remain **NOT TESTED**. One live audio event reached the Dashboard in 1,401 ms.

| Requirement | Result | Evidence |
| --- | --- | --- |
| Backend, MySQL and pipeline NORMAL/WARNING/ERROR | PASS | Normal health at startup; Backend WARNING at 1.5% 5xx and ERROR at 42.9% after controlled failures; Pipeline WARNING with 1 failed task and ERROR with 5; MySQL ERROR during isolated DB shutdown, NORMAL after restart. |
| API 5xx rate, average latency, recent errors | PASS | Dashboard displayed 1.5% and 1528.5 ms during test faults, plus the inserted error and actual `GET /api/admin/monitoring/dashboard` 500 logs. Metrics are process-local, rolling five-minute values. |
| PENDING, PROCESSING, COMPLETED, FAILED | PASS | SQL and Dashboard both showed 1, 1, 1, 5 respectively. A subsequent worker poll changed the test PENDING task to FAILED; both SQL and Dashboard reflected that transition. |
| Distinct active IDs in two minutes | PASS | Authenticated API `activeUsers=1`; simultaneous SQL `COUNT(DISTINCT anon_client_id)` with the UTC two-minute boundary returned 1. Earlier Dashboard values changed from 0 to 2 as test clients became active. |
| Audio by POI/language and actual playback start | PASS for manual playback | The guest browser played the real WAV fixture. MySQL recorded three `TAP_POI` events: POI 8001, `vi=2`, `en=1`; Dashboard showed the same counts. `AUDIO_COMPLETED` and preload were excluded. |
| Automatic versus manual listens | PASS for aggregation; NOT TESTED for real GPS autoplay | One explicitly synthetic `AUTO_PLAY` API event yielded SQL and Dashboard `auto=1`, `tap=3`, `autoRatio=25%`, POI 8001 total 4, `vi=2`, `en=2`. Actual GPS-driven start remains unverified. |
| Route starts versus views | PASS for API flow; NOT TESTED in guest UI | `GET /api/public/routes/8007` returned 200 and left SQL starts at 0. `POST /api/public/routes/8007/start` returned 200; recording the frontend's `ROUTE_SELECTED` event then made SQL and Dashboard show one start. |
| 5-second polling, no overlap, cleanup, loading/error/empty/stale | PASS in tests and visual checks; Network-panel trace NOT TESTED | Five polling tests pass, including timeout, no unresolved overlap, and unmount cleanup. Browser showed initial loading, empty listen/route lists, stale-data warning, and recovery. |
| Admin-only access | PASS | Anonymous Monitoring API returned 401; authenticated disposable Admin returned 200; a previously logged-in account changed to `LOCKED` returned 401. |
| Update within 10 seconds | PASS for one live event; wider SLA NOT TESTED | A second real WAV start reached the Dashboard counter in 1,401 ms. Repeated measurements and load conditions were not run. |

## Fresh test results

- Backend: Maven full suite, **147 tests, 0 failures, 0 errors, 18 skipped**. Run with Mockito as a Java agent and local loopback enabled.
- MySQL integration: opt-in `MonitoringUsageMySqlTest`, **2 tests, 0 failures, 0 errors, 0 skipped**, against a temporary random database created and removed by the test.
- Frontend: `node --test src/*.test.js`, **69 passed, 0 failed**.
- Frontend: `npm run build`, **PASS** with a non-fatal large-chunk warning.
- `git diff --check`, **PASS**.

## Faults reproduced and fixed

1. Polling cleared its in-flight marker at 4.5-second timeout even when the request promise remained unresolved. The next five-second tick started a second request. A new test failed with two concurrent requests, then passed after retaining the marker until settlement.
2. During a MySQL outage, Admin authentication queried the DB before the Monitoring Service and returned a generic HTTP 500 after 5,139 ms, exceeding the frontend's 4.5-second timeout. The interceptor now returns a minimal `DATABASE_UNAVAILABLE` HTTP 503 for an existing Admin session, without returning Monitoring data. The connection wait is 1.5 seconds; a direct outage probe returned 503 in **3,185 ms**. The browser displayed **MySQL: Lỗi** and a stale-data alert, then returned to **MySQL: Bình thường** after recovery. Anonymous sessions still receive 401.

## Evidence and limits

- The browser screenshots of normal counters, stale-data alert, and red MySQL badge were captured during this test session with Computer Use; the screenshot API did not save standalone image files into the repository.
- SQL readings and corresponding Dashboard values are listed above. The isolated runtime and database are disposable; credentials are kept under `C:\CNPM\.task08-runtime`, outside the Git checkout.
- The browser tool did not expose a Network-panel request trace. The five-second interval and cancellation were verified by tests, and a live event appeared in 1,401 ms.
- Actual GPS autoplay and the guest UI Route-start button still require an environment with controllable test geolocation; the synthetic auto event is labeled separately so it is not mistaken for a real audio start.
