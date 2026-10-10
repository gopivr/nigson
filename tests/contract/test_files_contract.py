import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class FileContracts(unittest.TestCase):
    def test_closed_purpose_bound_wire_shapes_and_private_callback(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());schemas=s['components']['schemas']
        for name in ('FileUpload','FileCompletion','AssetDTO','FileDownload','FileScanResult'):
            self.assertFalse(schemas[name]['additionalProperties'])
        self.assertEqual(schemas['FileUpload']['properties']['purpose']['enum'],['IMAGE','BROCHURE'])
        self.assertNotIn('objectKey',schemas['AssetDTO']['properties'])
        self.assertNotIn('classification',schemas['FileUpload']['properties'])
        op=s['paths']['/internal/v1/files/{id}/scan-result']['post']
        self.assertEqual(op['security'],[{'integrationWorkload':['files.scan.write']}])
        self.assertEqual({p['name'] for p in op['parameters'] if p['required']},{'id','If-Match','Idempotency-Key'})
        self.assertEqual(s['paths']['/api/v1/commerce/files/{id}/completion']['post']['responses']['202']['content']['application/json']['schema'],{'$ref':'#/components/schemas/AssetDTO'})
    def test_transfer_is_separate_additive_owned_increment(self):
        old=(ROOT/'services/commerce/src/main/resources/db/changelog/020-bp1-domain-tables.sql').read_text()
        new=(ROOT/'services/commerce/src/main/resources/db/changelog/023-bp1-file-transfer.sql').read_text()
        self.assertNotIn('CREATE TABLE commerce.file_transfer',old)
        self.assertIn('REFERENCES commerce.asset_reference',new)
        self.assertIn('REFERENCES commerce.party',new)
        self.assertNotIn('REFERENCES integration.',new)
        self.assertIn('Populated table commerce.file_transfer',(ROOT/'services/commerce/src/main/resources/db/changelog/023-bp1-file-transfer-rollback.sql').read_text())
