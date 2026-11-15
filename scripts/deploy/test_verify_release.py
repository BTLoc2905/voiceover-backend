import unittest

from verify_release import verify_release


SHA = "a" * 40
REPO = "BTLoc2905/voiceover-backend"
JOBS = ["backend-test", "frontend-test", "python-test", "mysql-integration-test", "e2e-test"]


def fixture():
    return {
        f"repos/{REPO}/environments/staging": {
            "protection_rules": [],
            "deployment_branch_policy": {"custom_branch_policies": True, "protected_branches": False},
        },
        f"repos/{REPO}/environments/staging/deployment-branch-policies?per_page=100": {
            "total_count": 1,
            "branch_policies": [{"name": "develop"}],
        },
        f"repos/{REPO}/actions/workflows/maven.yml/runs?head_sha={SHA}&event=push&per_page=100": {
            "workflow_runs": [{"id": 42, "head_sha": SHA, "head_branch": "develop", "event": "push",
                               "status": "completed", "conclusion": "success", "html_url": "https://example.test/run/42"}],
        },
        f"repos/{REPO}/actions/runs/42/jobs?per_page=100": {
            "jobs": [{"name": name, "conclusion": "success"} for name in JOBS],
        },
    }


class VerifyReleaseTest(unittest.TestCase):
    def check(self, payload):
        return verify_release(lambda path: payload[path], REPO, SHA, "refs/heads/develop")

    def test_accepts_exact_push_ci_with_develop_only_and_no_reviewers(self):
        self.assertEqual(self.check(fixture()), "https://example.test/run/42")

    def test_rejects_ci_for_different_sha(self):
        payload = fixture()
        payload[f"repos/{REPO}/actions/workflows/maven.yml/runs?head_sha={SHA}&event=push&per_page=100"]["workflow_runs"][0]["head_sha"] = "b" * 40
        with self.assertRaisesRegex(ValueError, "exact commit"):
            self.check(payload)

    def test_rejects_missing_required_job(self):
        payload = fixture()
        payload[f"repos/{REPO}/actions/runs/42/jobs?per_page=100"]["jobs"].pop()
        with self.assertRaisesRegex(ValueError, "e2e-test"):
            self.check(payload)

    def test_rejects_unprotected_staging(self):
        payload = fixture()
        payload[f"repos/{REPO}/environments/staging"]["deployment_branch_policy"] = None
        with self.assertRaisesRegex(ValueError, "custom develop branch rule"):
            self.check(payload)

    def test_rejects_wildcard_branch_rule(self):
        payload = fixture()
        payload[f"repos/{REPO}/environments/staging/deployment-branch-policies?per_page=100"]["branch_policies"] = [{"name": "*"}]
        with self.assertRaisesRegex(ValueError, "only develop"):
            self.check(payload)

    def test_rejects_additional_branch_rule(self):
        payload = fixture()
        payload[f"repos/{REPO}/environments/staging/deployment-branch-policies?per_page=100"]["branch_policies"].append({"name": "main"})
        with self.assertRaisesRegex(ValueError, "only develop"):
            self.check(payload)

    def test_rejects_non_develop_dispatch(self):
        with self.assertRaisesRegex(ValueError, "develop"):
            verify_release(lambda path: fixture()[path], REPO, SHA, "refs/heads/main")


if __name__ == "__main__":
    unittest.main()
