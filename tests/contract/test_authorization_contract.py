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
        self.assertEqual(len(spec['paths']), 29)
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()}, {f'API-{n:03}' for n in range(1,26)} | {'API-111', 'API-112', 'API-113', 'API-132', 'API-135', 'API-149', 'API-163', 'API-164'})
        put = spec['paths']['/api/v1/staff/users/{id}/roles']['put']
        self.assertEqual({p['name'] for p in put['parameters'] if p['required']}, {'id', 'If-Match', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        self.assertIn('412', put['responses'])
        self.assertFalse(spec['components']['schemas']['StaffRoleChange']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['StaffDTO']['required']), {'id', 'displayName', 'email', 'roles', 'version'})
        self.assertNotIn('CUSTOMER', spec['components']['schemas']['Role']['enum'])
