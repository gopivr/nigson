import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class PricingContracts(unittest.TestCase):
    def test_closed_commercial_quote_cannot_accept_browser_amounts_or_provider_mode(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());schemas=spec['components']['schemas']
        self.assertEqual(set(schemas['NewCheckoutQuote']['properties']),{'cartId','addressId','carrierQuoteId','couponCode'})
        self.assertFalse(schemas['NewCheckoutQuote']['additionalProperties'])
        self.assertEqual(set(schemas['CheckoutQuoteTotals']['properties']),{'subtotal','discount','tax','delivery','grandTotal'})
        self.assertEqual(set(schemas['PriceDTO']['properties']),{'skuId','unitAmount','currency','minimumQuantity','packMultiple','priceBookRevision','validUntil'})
    def test_copy_on_write_and_activation_use_parent_version_but_creates_do_not(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        for suffix in ('activation','revisions'):
            op=spec['paths']['/api/v1/staff/price-books/{id}/'+suffix]['post']
            self.assertIn('201',op['responses']);self.assertTrue({'If-Match','Idempotency-Key','X-CSRF-TOKEN'}<={p['name'] for p in op['parameters'] if p['required']})
        create=spec['paths']['/api/v1/staff/price-books']['post'];self.assertNotIn('If-Match',{p['name'] for p in create['parameters']})
        self.assertNotIn('code',spec['components']['schemas']['PriceRevision']['properties'])
    def test_only_retail_reads_are_public_and_every_price_response_is_noncacheable(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        read=spec['paths']['/api/v1/catalogue/skus/{id}/price']['get'];self.assertIn({},read['security'])
        self.assertEqual(read['responses']['200']['headers']['Cache-Control']['schema']['const'],'no-store')
        for path in ('/api/v1/staff/price-books','/api/v1/staff/price-books/{id}/activation','/api/v1/staff/price-books/{id}/revisions','/api/v1/checkout/quotes'):
            for op in spec['paths'][path].values():self.assertEqual(op['security'],[{'bffSession':[]}])
