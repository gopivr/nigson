"""Keep credential checks independent of removed project tracking files."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('source_secrets', ROOT / 'scripts/security/check_source_secrets.py')
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)


class SourceSecretTests(unittest.TestCase):
    def test_runtime_credentials_and_provider_keys_fail_without_exposing_values(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            subprocess.run(['git', 'init', '--quiet', folder], check=True)
            secret = 'sk_' + 'live_' + 'a' * 24
            (root / '.env').write_text('KEY=' + secret)
            subprocess.run(['git', 'add', '.env'], cwd=root, check=True)
            errors = scanner.check(root)
            self.assertTrue(any('Sensitive runtime file' in error for error in errors))
            self.assertTrue(any('Credential pattern' in error for error in errors))
            self.assertNotIn(secret, str(errors))

    def test_pem_fixture_is_rejected_but_source_boundary_literal_is_allowed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            subprocess.run(['git', 'init', '--quiet', folder], check=True)
            marker = '-----BEGIN ' + 'PRIVATE KEY-----'
            (root / 'adapter.java').write_text('return "' + marker + '";')
            subprocess.run(['git', 'add', '.'], cwd=root, check=True)
            self.assertEqual(scanner.check(root), [])
            (root / 'private.pem').write_text(marker + '\nexample\n')
            subprocess.run(['git', 'add', '.'], cwd=root, check=True)
            self.assertTrue(any('Credential pattern' in error for error in scanner.check(root)))
