"""Exercise the real startup script and JDK keystore generation in isolated files."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class IdentityTargetTLS(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'bin').mkdir()
        wrapper = ROOT/'infrastructure/docker/keycloak/production-entrypoint.sh'
        self.wrapper = self.root/'start.sh'
        self.wrapper.write_text(wrapper.read_text().replace('/opt/keycloak', str(self.root)))
        runner = self.root/'bin/kc.sh'
        runner.write_text('#!' + sys.executable + '\nimport json,os,sys\nfrom pathlib import Path\n'
                          'Path(os.environ["CAPTURE_PATH"]).write_text(json.dumps({"args":sys.argv[1:],"env":{k:v for k,v in os.environ.items() if k.startswith("KC_")}}))\n')
        runner.chmod(0o700)
        self.env = dict(os.environ, APP_ENV='test', KC_HOSTNAME='https://auth.synthetic.test',
                        KC_HOSTNAME_ADMIN='https://admin.synthetic.test', KC_DB_URL='jdbc:postgresql://db.synthetic.test:5432/keycloak',
                        KC_DB_USERNAME='nigson_identity', KC_DB_PASSWORD='isolated-test-password',
                        KC_PROXY_TRUSTED_ADDRESSES='10.40.0.0/24', NIGSON_IDENTITY_POLICY_REVISION='isolated-tls-test',
                        NIGSON_IDENTITY_DB_CA_PEM='isolated-db-ca-fixture', NIGSON_IDENTITY_TLS_MODE='generated',
                        CAPTURE_PATH=str(self.root/'capture.json'))

    def start(self, args=None):
        return subprocess.run(['bash', str(self.wrapper), *(args or ['start', '--optimized'])],
                              env=self.env, capture_output=True, text=True, timeout=30)

    def test_real_keystore_matches_hosts_is_private_and_rotates_on_restart(self):
        self.assertIsNotNone(shutil.which('keytool'), 'JDK keytool is required for TLS verification')
        certificates = []
        for _ in range(2):
            result = self.start()
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout, '')
            captured = json.loads((self.root/'capture.json').read_text())
            env = captured['env']
            self.assertEqual(captured['args'], ['start', '--optimized'])
            self.assertEqual(env['KC_HTTP_ENABLED'], 'false')
            self.assertNotIn('KC_HTTPS_CERTIFICATE_KEY_FILE', env)
            self.assertEqual(env['KC_HTTPS_KEY_STORE_TYPE'], 'PKCS12')
            keystore = Path(env['KC_HTTPS_KEY_STORE_FILE'])
            self.assertEqual(keystore.stat().st_mode & 0o777, 0o600)
            command_env = dict(os.environ, KEYSTORE_PASSWORD=env['KC_HTTPS_KEY_STORE_PASSWORD'])
            inspected = subprocess.run(['keytool', '-list', '-v', '-keystore', str(keystore), '-storepass:env', 'KEYSTORE_PASSWORD'],
                                       env=command_env, capture_output=True, text=True, check=True)
            self.assertIn('PrivateKeyEntry', inspected.stdout)
            self.assertIn('DNSName: auth.synthetic.test', inspected.stdout)
            self.assertIn('DNSName: admin.synthetic.test', inspected.stdout)
            exported = subprocess.run(['keytool', '-exportcert', '-rfc', '-alias', 'identity', '-keystore', str(keystore),
                                       '-storepass:env', 'KEYSTORE_PASSWORD'], env=command_env, capture_output=True, check=True)
            certificates.append(exported.stdout)
        self.assertNotEqual(*certificates)

    def test_generated_bootstrap_uses_same_tls_policy(self):
        self.env.update(KC_BOOTSTRAP_ADMIN_USERNAME='isolated-bootstrap', KC_BOOTSTRAP_ADMIN_PASSWORD='isolated-bootstrap-password')
        result = self.start(['bootstrap-admin', 'user'])
        self.assertEqual(result.returncode, 0, result.stderr)
        captured = json.loads((self.root/'capture.json').read_text())
        self.assertEqual(captured['args'][:2], ['bootstrap-admin', 'user'])
        self.assertIn('KC_HTTPS_KEY_STORE_FILE', captured['env'])

    def test_invalid_mode_or_mixed_certificate_sources_fail_closed(self):
        for additions in [{'NIGSON_IDENTITY_TLS_MODE':'http'}, {'NIGSON_IDENTITY_CERT_PEM':'unexpected'}]:
            with self.subTest(additions=additions):
                env = self.env.copy(); self.env.update(additions)
                result = self.start()
                self.assertEqual(result.returncode, 78)
                self.assertEqual(result.stderr.strip(), 'IDENTITY_CONFIGURATION_REJECTED')
                self.env = env

    def test_legacy_pair_is_still_supported_and_required(self):
        self.env['NIGSON_IDENTITY_TLS_MODE']='secret-pem'
        self.assertEqual(self.start().returncode, 78)
        self.env.update(NIGSON_IDENTITY_CERT_PEM='isolated-certificate', NIGSON_IDENTITY_KEY_PEM='isolated-private-key')
        result=self.start()
        self.assertEqual(result.returncode, 0, result.stderr)
        env=json.loads((self.root/'capture.json').read_text())['env']
        self.assertIn('KC_HTTPS_CERTIFICATE_KEY_FILE',env)
        self.assertNotIn('KC_HTTPS_KEY_STORE_FILE',env)
        self.assertEqual(Path(env['KC_HTTPS_CERTIFICATE_KEY_FILE']).stat().st_mode & 0o777, 0o600)
