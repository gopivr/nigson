"""Every declared BP2–BP5 API must remain in its owning service contract.

Compare method/path rather than operationId: AI uses stable descriptive IDs.
"""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ProductPhaseApiCoverage(unittest.TestCase):
    def test_declared_bp2_to_bp5_routes_exist_in_owner_contracts(self):
        master = (ROOT / 'BACKEND_IMPLEMENTATION_MASTER.md').read_text()
        specs = {owner: json.loads((ROOT / f'contracts/openapi/{owner}.yaml').read_text()) for owner in ('commerce','experience','integration','ai')}
        matched = 0
        pattern = r'^### (API-\d+) (GET|POST|PUT|PATCH|DELETE) (\S+)\n(.*?)(?=^### |^## |^# |\Z)'
        for api, method, path, body in re.findall(pattern, master, re.M | re.S):
            phase = re.search(r'Service / module / phase:\*\*\s*(commerce|experience|integration|ai)\s*/\s*[^\n]+?/\s*BP([2-5])', body)
            if not phase:
                continue
            owner, _ = phase.groups()
            with self.subTest(api=api, owner=owner):
                self.assertIn(method.lower(), specs[owner]['paths'].get(path, {}))
            matched += 1
        self.assertGreater(matched, 70, 'The phase audit must actually read the declared route inventory')

    def test_review_create_and_read_share_one_path_without_overwriting(self):
        spec = json.loads((ROOT / 'contracts/openapi/experience.yaml').read_text())
        operations = spec['paths']['/api/v1/reviews']
        self.assertEqual(operations['post']['operationId'], 'API-096')
        self.assertEqual(operations['get']['operationId'], 'API-097')
        self.assertIn('signedActor', operations['post']['security'][0])
        self.assertEqual(operations['get']['security'], [])
