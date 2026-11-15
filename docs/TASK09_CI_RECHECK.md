# Task 09 — CI recheck after Tasks 04–08

Checked: 2026-11-15. Latest remote `develop` at the time of review: `cb251a9db9e60a9ab2febd53e1c6bba8bd6f76d9` (merge PR #27). [GitHub Actions run 43668662541](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43668662541) is a completed successful `push` run for exactly that SHA. The screenshot supplied by the requester showed the older PR #26 run 43661433687 and was not sufficient by itself for the latest source.

| Job | Result on `cb251a9` | Tests / build |
| --- | --- | --- |
| `backend-test` | PASS | 147 tests, 0 failures, 0 errors, 18 skipped; Maven `verify` and executable JAR build succeeded. The 18 skips include opt-in MySQL and live Edge tests; the MySQL job is intended to run the database tests separately. |
| `frontend-test` | PASS | 69 passed, 0 failed, 0 skipped; Vite production build succeeded. The build emitted a non-fatal >500 kB chunk warning. |
| `python-test` | PASS | 7 passed, 0 failed. |
| `mysql-integration-test` | PASS but missing Task 08 coverage | 14 passed, 0 failed, 0 skipped across `ContentPipelineMySqlTest` (9), `PoiHiddenMySqlTest` (2), and `RouteIndependenceMySqlTest` (3). The job's Surefire report check also passed. It did not run `MonitoringUsageMySqlTest` (2 new tests). |
| `publish-image` | SKIPPED as configured | The workflow permits this job only on a push to `main`, after all four test jobs pass. Skipping it on `develop` is correct. The last `main` run [42954748402](https://github.com/BTLoc2905/voiceover-backend/actions/runs/42954748402), on older SHA `24b09d9`, successfully ran `publish-image`; it is not a Docker build of the current source. |

## Coverage gap and correction

The latest GitHub run was green, but `MonitoringUsageMySqlTest` was skipped by `backend-test` and absent from `mysql-integration-test`. The local workflow change enables `monitoring.mysql.integration`, provides the isolated test MySQL URL and password, includes the class in Maven's test selection, and requires its Surefire report to show executed tests with zero skips.

Local verification against isolated MySQL 8.4.13: all four selected classes ran, **16 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. The workflow YAML parsed successfully and `git diff --check` passed. No application tests were removed or disabled. The local machine has no Docker CLI, so the current source's Docker build was not rerun here.

PR [#28](https://github.com/BTLoc2905/voiceover-backend/pull/28) contains the correction. Its [first CI run](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43671398711) on commit `2df7fa78aea5943759baa46fae2d769ec964e909` completed successfully: backend 147 tests (0 failures, 18 expected opt-in skips), frontend 69 passed with production build, Python 7 passed, and MySQL **16 tests, 0 failures, 0 errors, 0 skipped**. The MySQL log and report-check step both confirm `MonitoringUsageMySqlTest` ran 2 tests with no skips. `publish-image` was skipped as expected on a PR; the workflow only publishes on a push to `main`.
