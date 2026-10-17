"""Negative checks for onboarding, including fresh checkout prerequisites."""

import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("check_repository", ROOT / "scripts/check_repository.py")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class RepositoryChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="nigson-foundation-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in checker.REQUIRED_FILES:
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        for name in (ROOT / "docs/implementation/repository-paths.txt").read_text().splitlines():
            (self.root / name).mkdir(parents=True, exist_ok=True)
            (self.root / name / ".gitkeep").touch()
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)

    def test_complete_tracked_tree(self):
        self.assertEqual([], checker.check(self.root, tracked=True))

    def test_missing_required_directory(self):
        shutil.rmtree(self.root / "tests/mocks/fedex")
        self.assertTrue(any("Missing required directory: tests/mocks/fedex" in error
                            for error in checker.check(self.root)))

    def test_missing_status_task(self):
        file = self.root / "IMPLEMENTATION_STATUS.md"
        file.write_text(file.read_text().replace("- [x] MSG-001 —", "- MSG-001 —"))
        self.assertTrue(any("Task registry mismatch" in error for error in checker.check(self.root)))

    def test_duplicate_status_task(self):
        file = self.root / "IMPLEMENTATION_STATUS.md"
        with file.open("a") as output:
            output.write("\n- [ ] MSG-001 — duplicate\n")
        self.assertTrue(any("Duplicate task IDs" in error for error in checker.check(self.root)))

    def test_tracked_runtime_secret_file_rejected(self):
        (self.root / ".env").write_text("APP_ENV=local\n")
        subprocess.run(["git", "add", "-f", ".env"], cwd=self.root, check=True)
        self.assertTrue(any("Sensitive runtime file tracked: .env" in error
                            for error in checker.check(self.root)))

    def test_real_provider_key_pattern_rejected(self):
        (self.root / "credential.txt").write_text("sk_" + "live_" + "a" * 24)
        subprocess.run(["git", "add", "credential.txt"], cwd=self.root, check=True)
        self.assertTrue(any("Credential pattern" in error for error in checker.check(self.root)))

    def test_untracked_onboarding_file_rejected(self):
        subprocess.run(["git", "rm", "--cached", "README.md"], cwd=self.root,
                       check=True, capture_output=True)
        self.assertTrue(any("Required files not tracked" in error
                            for error in checker.check(self.root, tracked=True)))

    def test_manifest_path_escape_rejected(self):
        file = self.root / "docs/implementation/repository-paths.txt"
        with file.open("a") as output:
            output.write("../outside\n")
        self.assertTrue(any("Unsafe repository manifest" in error
                            for error in checker.check(self.root)))

    def test_disallowed_deployment_tree_rejected(self):
        (self.root / "helm").mkdir()
        self.assertTrue(any("Out-of-scope" in error for error in checker.check(self.root)))


if __name__ == "__main__":
    unittest.main()
