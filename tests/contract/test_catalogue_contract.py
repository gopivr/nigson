import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class CatalogueContracts(unittest.TestCase):
    def test_closed_public_shapes_and_private_assets(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        expected={'ProductDTO':{'id','name','slug','description','specifications','brand','categories','skus','assets','revision'},'ProductSummaryDTO':{'id','name','slug','brandName','imageUrl','available','revision'},'SkuAdminDTO':{'id','code','variantName','unit','quantityScale','packSize','version'},'TaxonomyDTO':{'id','name','slug','parentId','active','version'}}
        for name,fields in expected.items():
            schema=spec['components']['schemas'][name]
            self.assertEqual(set(schema['required']),fields)
            self.assertFalse(schema['additionalProperties'])
        self.assertEqual(spec['components']['schemas']['CatalogueAsset']['properties']['purpose']['enum'],['IMAGE','BROCHURE'])
        self.assertEqual(spec['components']['schemas']['ProductAdminDTO']['properties']['assets']['items'],{'$ref':'#/components/schemas/CatalogueAdminAsset'})
        self.assertNotIn('code',spec['components']['schemas']['SkuPatch']['properties'])
        self.assertEqual(spec['components']['schemas']['SkuPatch']['required'],['carrierPackaging'])
    def test_exact_public_security_and_versioned_commands(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        for path in ['/api/v1/catalogue/products','/api/v1/catalogue/products/{id}','/api/v1/catalogue/categories','/api/v1/catalogue/brands']:
            op=spec['paths'][path]['get']
            self.assertEqual(op['security'],[])
            self.assertNotIn('content',op['responses']['304'])
            self.assertIn('429',op['responses'])
        for path,method in [('/api/v1/staff/products/{id}','patch'),('/api/v1/staff/products/{id}/publication','post'),('/api/v1/staff/products/{id}/skus','post'),('/api/v1/staff/skus/{id}','patch'),('/api/v1/staff/brands/{id}','patch'),('/api/v1/staff/categories/{id}','patch')]:
            op=spec['paths'][path][method]
            self.assertTrue(all(p['required'] for p in op['parameters']))
            self.assertIn('If-Match',{p['name'] for p in op['parameters']})
            self.assertIn('412',op['responses'])
            self.assertNotIn('202',op['responses'])
