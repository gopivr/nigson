import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class ProviderContracts(unittest.TestCase):
    def test_closed_job_shapes_and_versioned_signed_retry(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        job=spec['components']['schemas']['JobDTO']
        self.assertFalse(job['additionalProperties'])
        self.assertEqual(set(job['properties']),{'id','state','attemptCount','nextAttemptAt','lastErrorCode'})
        self.assertEqual(set(spec['components']['schemas']['JobRetry']['properties']),{'reason'})
        op=spec['paths']['/api/v1/staff/integration-jobs/{id}/retry']['post']
        self.assertEqual({p['name'] for p in op['parameters'] if p['required']},{'id','If-Match','Idempotency-Key','X-CSRF-TOKEN'})
        self.assertIn('202',op['responses'])
        self.assertIn('ETag',op['responses']['202']['headers'])
    def test_read_and_retry_follow_specific_compiled_scopes(self):
        routes=json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        registry={r['api']:r for r in routes}
        self.assertEqual(registry['API-140']['scope'],'jobs.read')
        self.assertEqual(registry['API-141']['scope'],'jobs.retry_WITH_SIGNED_STAFF')
        self.assertEqual(registry['API-141']['owner'],'integration')
