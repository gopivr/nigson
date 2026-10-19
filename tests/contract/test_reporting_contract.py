import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

class CommerceReportingContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.api=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())

    def test_api_075_has_closed_bounded_request_and_uncached_sanitized_response(self):
        operation=self.api['paths']['/api/v1/staff/reports/commerce']['get']
        self.assertEqual(operation['operationId'],'API-075')
        self.assertEqual(operation['security'],[{'bffSession':[]}])
        self.assertEqual({p['name'] for p in operation['parameters']},{'from','to','currency','organizationId'})
        self.assertTrue(all(p['required'] for p in operation['parameters'] if p['name'] in {'from','to','currency'}))
        schema=self.api['components']['schemas']['CommerceReport']
        self.assertEqual(schema['properties']['products']['maxItems'],100)
        self.assertEqual(schema['properties']['stock']['maxItems'],100)
        self.assertEqual(operation['responses']['200']['headers']['Cache-Control']['schema']['const'],'no-store')
        self.assertNotIn('requestBody',operation)

    def test_reporting_error_is_registered_and_route_has_one_owner(self):
        codes=json.loads((ROOT/'contracts/problem/codes.json').read_text())
        routes=json.loads((ROOT/'contracts/problem/routes.json').read_text())
        self.assertEqual(codes['REPORTING_RULE_VIOLATION']['status'],422)
        self.assertEqual(routes.count('/api/v1/staff/reports/commerce'),1)
