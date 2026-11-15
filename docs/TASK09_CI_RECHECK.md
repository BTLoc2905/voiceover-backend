# Task 09 — CI recheck after Tasks 04–08

Checked: 2026-11-15. Latest remote `develop` at the time of review: `cb251a9db9e60a9ab2febd53e1c6bba8bd6f76d9` (merge PR #27). [GitHub Actions run 43668662541](https://github.com/BTLoc2905/voiceover-backend/actions/runs/43668662541) is a completed successful `push` run for exactly that SHA. The screenshot supplied by the requester showed the older PR #26 run 43661433687 and was not sufficient by itself for the latest source.

| Job | Result on `cb251a9` | Tests / build |
| --- | --- | --- |
| `backend-test` | PASS | 147 tests, 0 failures, 0 errors, 18 skipped; Maven `verify` and executable JAR build succeeded. The 18 skips include opt-in MySQL and live Edge tests; the MySQL job is intended to run the database tests separately. |
| `frontend-test` | PASS | 69 passed, 0 failed, 0 skipped; Vite production build succeeded. The build emitted a non-fatal >500 kB chunk warning. |
| `python-test` | PASS | 7 passed, 0 failed. |
| `mysql-integration-test` | PASS but missing Task 08 coverage | 14 passed, 0 failed, 0 skipped across `ContentPipelineMySqlTest` (9), `PoiHiddenMySqlTest` (2), and `RouteIndependenceMySqlTest` (3). The job's Surefire report check also passed. It did not run `MonitoringUsageMySqlTest` (2 new tests). |
| `publish-image` | SKIPPED as configured | The workflow permits this job only on a push to `main`, after all four test jobs pass. Skipping it on `develop` is correct. The last `main` run [42954748402](https://github.com/BTLoc2905/voiceover-backend/actions/runs/42954748402), on older SHA `24b09d9`, successfully ran `publish-image`; it is not a Docker build of the current source. |

## Coverage gap and local correction

The latest GitHub run was green, but `MonitoringUsageMySqlTest` was skipped by `backend-test` and absent from `mysql-integration-test`. The local workflow change enables `monitoring.mysql.integration`, provides the isolated test MySQL URL and password, includes the class in Maven's test selection, and requires its Surefire report to show executed tests with zero skips.

Local verification against isolated MySQL 8.4.13: all four selected classes ran, **16 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. The workflow YAML parsed successfully and `git diff --check` passed. No application tests were removed or disabled. The local machine has no Docker CLI, so the current source's Docker build was not rerun here.

**Remaining acceptance step:** push the workflow correction and observe a new GitHub Actions run on its PR or merge. Until then, GitHub has proved the latest `develop` is green for the previous 14-test MySQL selection, while the corrected 16-test selection is proved only locally. The requester asked to defer pushing local work, so this correction remains unpushed.
