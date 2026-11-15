import json
import tempfile
import unittest
from pathlib import Path

from staging import deploy, rollback


OLD_BACKEND = "ghcr.io/example/voicemap-backend:sha-" + "a" * 40
OLD_FRONTEND = "ghcr.io/example/voicemap-frontend:sha-" + "a" * 40
NEW_BACKEND = "ghcr.io/example/voicemap-backend:sha-" + "b" * 40
NEW_FRONTEND = "ghcr.io/example/voicemap-frontend:sha-" + "b" * 40


class FakeOps:
    def __init__(self):
        self.old = (OLD_BACKEND, OLD_FRONTEND)
        self.rank = 19
        self.fail_backup = False
        self.fail_smoke = False
        self.calls = []

    def preflight(self):
        self.calls.append("preflight")

    def current_images(self):
        return self.old

    def database_rank(self):
        return self.rank

    def backup_database(self, target):
        self.calls.append("backup")
        if self.fail_backup:
            raise RuntimeError("backup failed")
        target.write_text("isolated SQL backup")

    def pull_and_up(self, backend, frontend):
        self.calls.append(("up", backend, frontend))

    def smoke(self):
        self.calls.append("smoke")
        if self.fail_smoke:
            raise RuntimeError("smoke failed")


class StagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.ops = FakeOps()

    def tearDown(self):
        self.temp.cleanup()

    def test_success_records_previous_images_and_backup(self):
        deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        state = json.loads((self.root / "releases/current.json").read_text())
        self.assertEqual(state["previous_backend"], OLD_BACKEND)
        self.assertEqual(state["previous_frontend"], OLD_FRONTEND)
        self.assertTrue(Path(state["backup_path"]).is_file())
        self.assertIn(("up", NEW_BACKEND, NEW_FRONTEND), self.ops.calls)

    def test_backup_failure_never_restarts_application(self):
        self.ops.fail_backup = True
        with self.assertRaisesRegex(RuntimeError, "backup failed"):
            deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        self.assertFalse(any(isinstance(x, tuple) and x[0] == "up" for x in self.ops.calls))

    def test_smoke_failure_keeps_previous_images_for_manual_recovery(self):
        self.ops.fail_smoke = True
        with self.assertRaisesRegex(RuntimeError, "smoke failed"):
            deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        pending = json.loads((self.root / "releases/pending.json").read_text())
        self.assertEqual(pending["previous_backend"], OLD_BACKEND)
        self.assertEqual(pending["previous_frontend"], OLD_FRONTEND)
        self.assertEqual(sum(1 for call in self.ops.calls if isinstance(call, tuple) and call[0] == "up"), 1)

    def test_rollback_refuses_changed_flyway_schema(self):
        deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        self.ops.rank = 20
        with self.assertRaisesRegex(RuntimeError, "Flyway"):
            rollback(self.ops, self.root)
        self.assertEqual(sum(1 for call in self.ops.calls if isinstance(call, tuple) and call[0] == "up"), 1)

    def test_rollback_restores_exact_previous_refs_when_schema_unchanged(self):
        deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        rollback(self.ops, self.root)
        self.assertIn(("up", OLD_BACKEND, OLD_FRONTEND), self.ops.calls)

    def test_first_release_has_no_rollback_image(self):
        self.ops.old = (None, None)
        self.ops.rank = None
        deploy(self.ops, self.root, NEW_BACKEND, NEW_FRONTEND)
        with self.assertRaisesRegex(RuntimeError, "previous"):
            rollback(self.ops, self.root)

    def test_rejects_latest_or_mismatched_image_tags(self):
        with self.assertRaisesRegex(ValueError, "SHA"):
            deploy(self.ops, self.root, NEW_BACKEND.replace(":sha-", ":latest-"), NEW_FRONTEND)
        with self.assertRaisesRegex(ValueError, "same commit"):
            deploy(self.ops, self.root, NEW_BACKEND, OLD_FRONTEND)


if __name__ == "__main__":
    unittest.main()
