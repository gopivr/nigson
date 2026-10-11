"""Qualification rejects invalid config before builds or provider HTTP, without printing keys."""
import importlib.util
import os
from pathlib import Path
import runpy
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('provider_readiness', ROOT/'scripts/platform/provider_readiness.py')
readiness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(readiness)

class PaystackReadiness(unittest.TestCase):
    def config(self):
        # These are syntactic fixtures only; positive tests invoke pure validation, never HTTP.
        return {'ENV': 'stage', 'PROVIDER': 'paystack', 'PAYSTACK_MODE': 'TEST',
                'PAYSTACK_BASE_URL': 'https://api.paystack.co',
                'PAYSTACK_SECRET_KEY': 'sk_test_abcdef0123456789abcdef0123456789abcdef01',
                'PAYSTACK_QUALIFICATION_REFERENCE': 'qualification-123',
                'PAYSTACK_QUALIFICATION_KOBO': '123456'}

    def blocked(self, env):
        with patch.dict(os.environ, env, clear=True), patch('subprocess.run', side_effect=AssertionError('Build/HTTP gate bypassed')) as launch, patch.object(sys, 'path', [str(ROOT/'scripts/platform'), *sys.path]):
            with self.assertRaises(SystemExit) as failure:
                runpy.run_path(str(ROOT/'scripts/platform/provider_live_check.py'), run_name='__main__')
            message = str(failure.exception)
            self.assertTrue(message.startswith('BLOCKED:'))
            self.assertIn('no provider call made', message)
            if env.get('PAYSTACK_SECRET_KEY'):
                self.assertNotIn(env['PAYSTACK_SECRET_KEY'], message)
            launch.assert_not_called()

    def test_required_settings_fail_before_build(self):
        for setting in self.config():
            with self.subTest(setting=setting):
                env = self.config(); env[setting] = ''; self.blocked(env)

    def test_known_fixture_credentials_cannot_reach_provider(self):
        for value in ('sk_mock_nigson', 'sk_test_configuration_validation_only', 'sk_test_synthetic', 'sk_test_fixture', 'sk_test_shape_only', 'sk_test_dummy', 'sk_test_placeholder', 'sk_test_changeme', 'sk_test_your_secret', 'sk_test_0000000000000', 'sk_test_value\n', 'sk_live_ab12'):
            with self.subTest(value=value):
                env = self.config(); env['PAYSTACK_SECRET_KEY'] = value; self.blocked(env)

    def test_mode_and_official_target_checked_before_build(self):
        for setting, values in {'ENV': ('test', 'prod', 'local'), 'PAYSTACK_MODE': ('MOCK', 'LIVE', 'test'), 'PAYSTACK_BASE_URL': ('http://api.paystack.co', 'https://api.paystack.co.evil.invalid', 'https://api.paystack.co:443', 'https://operator@api.paystack.co', 'https://api.paystack.co?token=1', 'https://127.0.0.1')}.items():
            for value in values:
                with self.subTest(setting=setting, value=value):
                    env = self.config(); env[setting] = value; self.blocked(env)

    def test_expected_money_is_bounded_integer_before_build(self):
        for value in ('0', '-1', '1.00', '01', '1e2', '9223372036854775808', '123456789012345678901'):
            with self.subTest(value=value):
                env = self.config(); env['PAYSTACK_QUALIFICATION_KOBO'] = value; self.blocked(env)

    def test_provider_and_reference_cannot_inject_paths(self):
        for setting, value in (('PROVIDER', 'other'), ('PAYSTACK_QUALIFICATION_REFERENCE', '../reference'), ('PAYSTACK_QUALIFICATION_REFERENCE', 'ref?key=1'), ('PAYSTACK_QUALIFICATION_REFERENCE', 'x'*101)):
            with self.subTest(setting=setting, value=value):
                env = self.config(); env[setting] = value; self.blocked(env)

    def test_pure_shape_validation_does_not_qualify_accounts(self):
        self.assertEqual(readiness.validate(self.config()), ('paystack',))
        env = self.config(); env['PAYSTACK_BASE_URL'] += '/'
        self.assertEqual(readiness.validate(env), ('paystack',))

    def test_all_providers_preflight_before_any_selected_provider_runs(self):
        env = self.config(); env['PROVIDER'] = 'all'
        self.blocked(env)

if __name__ == '__main__':
    unittest.main()
