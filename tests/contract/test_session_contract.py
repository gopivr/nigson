import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class SessionContracts(unittest.TestCase):
    def test_exact_public_session_contracts(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        self.assertEqual(set(s['components']['schemas']['SessionDTO']['required']),{'authenticated','partyId','displayName','roles','organizationMemberships','expiresAt','csrfToken'})
        for path in ('/bff/login','/bff/callback','/bff/session'):
            self.assertEqual(s['paths'][path]['get']['security'],[])
        self.assertIn('302',s['paths']['/bff/callback']['get']['responses'])
        logout=s['paths']['/bff/logout']['post']
        self.assertNotIn('content',logout['responses']['204'])
        self.assertIn('503',logout['responses'])
        self.assertFalse(logout['requestBody']['content']['application/json']['schema']['additionalProperties'])
        self.assertNotIn('accessToken',s['components']['schemas']['SessionDTO']['properties'])
