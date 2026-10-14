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
        self.assertEqual(len(spec['paths']), 69)
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()}, {f'API-{n:03}' for n in range(1,26)} | {'API-111', 'API-112', 'API-113', 'API-132', 'API-135', 'API-149', 'API-163', 'API-164', 'API-122', 'API-133', 'API-165', 'API-045', 'API-046', 'API-170', 'API-029', 'API-031', 'API-162', 'API-169', 'API-026', 'API-027', 'API-028', 'API-036', 'API-150', 'API-151', 'API-032', 'API-033', 'API-034', 'API-035', 'API-047', 'API-048', 'API-049', 'API-050', 'API-171', 'API-174', 'API-037', 'API-038', 'API-039', 'API-040', 'API-043', 'API-044', 'API-121', 'API-041', 'API-042', 'API-052', 'API-053', 'API-054', 'API-118', 'API-181', 'API-119', 'API-120'})
        put = spec['paths']['/api/v1/staff/users/{id}/roles']['put']
        self.assertEqual({p['name'] for p in put['parameters'] if p['required']}, {'id', 'If-Match', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        self.assertIn('412', put['responses'])
        self.assertFalse(spec['components']['schemas']['StaffRoleChange']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['StaffDTO']['required']), {'id', 'displayName', 'email', 'roles', 'version'})
        self.assertNotIn('CUSTOMER', spec['components']['schemas']['Role']['enum'])
