import json
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]

class CmsContentContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec=json.loads((ROOT/'contracts/openapi/experience.yaml').read_text())
        cls.routes={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        cls.operations={op['operationId']:op for path in cls.spec['paths'].values() for op in path.values()}

    def test_exact_staff_workload_and_signed_actor_routes(self):
        expected={
          'API-078':('POST','/api/v1/staff/content','content.write_WITH_SIGNED_ACTOR'),
          'API-079':('POST','/api/v1/staff/content/{id}/revisions','content.write_WITH_SIGNED_ACTOR'),
          'API-080':('POST','/api/v1/staff/content-revisions/{id}/review','content.review_WITH_SIGNED_ACTOR'),
          'API-081':('POST','/api/v1/staff/content-revisions/{id}/approval','content.approve_WITH_SIGNED_ACTOR'),
          'API-082':('POST','/api/v1/staff/content-revisions/{id}/publication','content.publish_WITH_SIGNED_ACTOR'),
          'API-083':('GET','/api/v1/staff/content-revisions/{id}/preview','content.read_WITH_SIGNED_ACTOR'),
          'API-086':('PUT','/api/v1/staff/channel-configurations/{id}','channels.write_WITH_SIGNED_ADMIN'),
        }
        for api,(method,path,scope) in expected.items():
            self.assertEqual((self.routes[api]['method'],self.routes[api]['path'],self.routes[api]['scope']),(method,path,scope))
            operation=self.operations[api]
            self.assertEqual(operation['security'],[{'workload':[scope],'signedActor':[]}])
        self.assertEqual(self.routes['API-082']['client'],'nigson-commerce')

    def test_publication_and_preview_contract_boundaries(self):
        for api in ['API-076','API-077','API-084','API-085']:
            self.assertEqual(self.operations[api]['security'],[])
        self.assertIn('Idempotency-Key',[p['name'] for p in self.operations['API-082']['parameters']])
        self.assertIn('If-Match',[p['name'] for p in self.operations['API-082']['parameters']])
        self.assertEqual(self.operations['API-082']['requestBody']['content']['application/json']['schema']['$ref'],'#/components/schemas/ContentPublication')
        self.assertIn('X-Nigson-Actor',self.spec['components']['securitySchemes']['signedActor']['name'])

if __name__=='__main__':unittest.main()
