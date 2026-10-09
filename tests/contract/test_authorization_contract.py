import json
from pathlib import Path
import subprocess
import sys
import unittest
ROOT = Path(__file__).resolve().parents[2]
class AuthorizationContracts(unittest.TestCase):
    def test_reviewed_generation_and_exact_implemented_routes(self):
        subprocess.run([sys.executable, 'scripts/contracts/authorization.py', '--check'], cwd=ROOT, check=True)
        spec = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())
        self.assertEqual(set(spec['paths']), {'/api/v1/staff/users', '/api/v1/staff/users/{id}/roles', '/api/v1/me', '/api/v1/me/addresses', '/api/v1/me/addresses/{id}'})
        put = spec['paths']['/api/v1/staff/users/{id}/roles']['put']
        self.assertEqual({p['name'] for p in put['parameters'] if p['required']}, {'id', 'If-Match', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        self.assertIn('412', put['responses'])
        self.assertFalse(spec['components']['schemas']['StaffRoleChange']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['StaffDTO']['required']), {'id', 'displayName', 'email', 'roles', 'version'})
        self.assertNotIn('CUSTOMER', spec['components']['schemas']['Role']['enum'])
