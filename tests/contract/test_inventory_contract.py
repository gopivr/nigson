import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class InventoryContracts(unittest.TestCase):
    def test_stock_is_closed_scaled_strings_with_explicit_versioned_adjustment(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());stock=s['components']['schemas']['StockDTO']
        self.assertFalse(stock['additionalProperties']);self.assertEqual(set(stock['properties']),{'skuId','locationId','onHand','reserved','allocated','available','version'})
        for key in ('onHand','reserved','allocated','available'):self.assertEqual(stock['properties'][key]['type'],'string')
        op=s['paths']['/api/v1/staff/stock-adjustments']['post'];self.assertEqual({p['name'] for p in op['parameters'] if p['required']},{'If-Match','Idempotency-Key','X-CSRF-TOKEN'});self.assertIn('201',op['responses'])
    def test_locations_do_not_accept_initial_stock_or_client_authority(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());value=s['components']['schemas']['NewStockLocation']
        self.assertFalse(value['additionalProperties']);self.assertEqual(set(value['properties']),{'code','name'})
        event=json.loads((ROOT/'contracts/events/EVT-005-inventory.availability.changed.v1.json').read_text())
        self.assertEqual(set(event['properties']['payload']['properties']),{'sku_id','location_id','available'})
