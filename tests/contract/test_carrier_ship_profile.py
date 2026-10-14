import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CarrierShipProfile(unittest.TestCase):
    def test_synthetic_request_covers_reviewed_provider_required_fields(self):
        profile = json.loads((ROOT / 'contracts/providers/fedex/ship-profile-2026-10-14.json').read_text())
        request = json.loads((ROOT / 'contracts/providers/fedex/ship-request.json').read_text())
        # Account comes exclusively from the configured adapter; this fixture never supplies one.
        self.assertEqual(set(profile['requiredRoot']) - request.keys(), {'accountNumber'})
        shipment = request['requestedShipment']
        self.assertTrue(set(profile['requiredShipment']).issubset(shipment))
        self.assertEqual(shipment['totalPackageCount'], len(shipment['requestedPackageLineItems']))
        self.assertEqual(request['labelResponseOptions'], 'LABEL')
        self.assertEqual(shipment['labelSpecification']['imageType'], 'PDF')
        self.assertIn(shipment['labelSpecification']['labelStockType'], profile['supportedPaperStocks'])
        self.assertIn(shipment['shippingChargesPayment']['paymentType'], profile['supportedBilling'])
        self.assertIn(request['processingOptionType'], profile['processingOptions'])
        for number, package in enumerate(shipment['requestedPackageLineItems'], start=1):
            self.assertEqual(package['sequenceNumber'], number)
            self.assertEqual(package['groupPackageCount'], 1)
            self.assertEqual(package['weight']['units'], 'KG')
            self.assertEqual(package['customerReferences'][0]['customerReferenceType'], 'CUSTOMER_REFERENCE')
        self.assertIn('developer.fedex.com', profile['source'])
        self.assertRegex(profile['upstream']['sha256'], r'^[a-f0-9]{64}$')


if __name__ == '__main__':
    unittest.main()
