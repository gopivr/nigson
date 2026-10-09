"""Identity policy and authenticated backup checks; no production account defaults."""
import importlib.util
import json
import re
from pathlib import Path
import sys
import subprocess
import os
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'scripts/identity'))
from backup import decrypt, encrypt
from realm_policy import realm_policy


class IdentityOperationsTest(unittest.TestCase):
    def policy(self, phase=1):
        return realm_policy('isolated-test','https://bff.synthetic.test/bff/callback',phase,'synthetic-test-policy','test',3600)
    def test_exact_confidential_pkce_and_no_bp1_ai(self):
        policy = self.policy()
        bff = policy['clients'][0]
        self.assertFalse(bff['publicClient']);self.assertFalse(bff['directAccessGrantsEnabled'])
        self.assertEqual(bff['attributes']['pkce.code.challenge.method'],'S256')
        self.assertEqual(len(bff['redirectUris']),1)
        self.assertNotIn('nigson-ai',{c['clientId'] for c in policy['clients']})
        self.assertIn('nigson-ai',{c['clientId'] for c in self.policy(5)['clients']})
    def test_scopes_are_authoritative_operation_grants(self):
        rows = json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        policy = self.policy()
        for client in policy['clients'][1:]:
            wanted={r['scope'] for r in rows if r['client']==client['clientId'] and r['owner']!='ai' and r['phase']<=1}
            self.assertEqual(set(client['defaultClientScopes']),wanted)
            self.assertFalse(client['fullScopeAllowed']);self.assertEqual(client['optionalClientScopes'],[])
    def test_registry_matches_master_internal_contracts(self):
        master=(ROOT/'BACKEND_IMPLEMENTATION_MASTER.md').read_text()
        rows=json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        expected=set()
        for match in re.finditer(r'^### (API-\d+) (\w+) (/internal/[^\n]+)\n(.*?)(?=^### |^# |\Z)',master,re.M|re.S):
            metadata=re.search(r'Service / module / phase:\*\* (\w+) / [^/]+ / BP([1-5])',match[4]);self.assertIsNotNone(metadata)
            authorization=re.search(r'Authorization:\*\* ([^\n]+)',match[4]);self.assertIsNotNone(authorization)
            for client,scope in re.findall(r'SERVICE_(COMMERCE|EXPERIENCE|INTEGRATION|AI):([A-Za-z0-9._-]+)',authorization[1]):
                expected.add((match[1],match[2],match[3],metadata[1],'nigson-'+client.lower(),scope,int(metadata[2])))
        actual={(r['api'],r['method'],r['path'],r['owner'],r['client'],r['scope'],r['phase']) for r in rows}
        self.assertEqual(actual,expected)
        self.assertEqual(len(actual),len(rows))
    def test_no_mock_production_configuration(self):
        for callback in ('https://bff.synthetic.test/bff/callback','http://localhost/bff/callback','https://approved.invalid/*','https://bff.synthetic.test/bff/callback?override=true'):
            with self.assertRaises(ValueError): realm_policy('isolated-test',callback,1,'synthetic','prod',3600)
        with self.assertRaises(ValueError): realm_policy('master','https://bff.synthetic.test/bff/callback',1,'synthetic','test',3600)
    def test_backup_authentication_revision_and_exclusive_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);source=root/'dump';key=root/'key';cipher=root/'encrypted';restored=root/'restored'
            source.write_bytes(b'test-identity-data'*100000);key.write_bytes(bytes(range(32)))
            source.chmod(0o600);key.chmod(0o600)
            encrypt(source,cipher,key,'synthetic-policy')
            decrypt(cipher,restored,key,'synthetic-policy');self.assertEqual(restored.read_bytes(),source.read_bytes())
            with self.assertRaises(FileExistsError): encrypt(source,cipher,key,'synthetic-policy')
            with self.assertRaises(ValueError): decrypt(cipher,root/'wrong-revision',key,'other-policy')
            raw=bytearray(cipher.read_bytes());raw[-1]^=1;cipher.write_bytes(raw)
            with self.assertRaises(Exception): decrypt(cipher,root/'tampered',key,'synthetic-policy')
            self.assertFalse((root/'tampered').exists());self.assertEqual(list(root.glob('*.verifying-*')),[])
    def test_optimized_wrapper_fails_closed_before_network(self):
        wrapper = str(ROOT/'infrastructure/docker/keycloak/production-entrypoint.sh')
        for mode,args in [('local',['start','--optimized']),('prod',['start-dev']),('prod',['start','--optimized']),('prod',['bootstrap-admin','user'])]:
            run=subprocess.run(['bash',wrapper,*args],env={'PATH':os.environ['PATH'],'APP_ENV':mode},capture_output=True,text=True,timeout=5)
            self.assertEqual(run.returncode,78)
            self.assertEqual(run.stderr.strip(),'IDENTITY_CONFIGURATION_REJECTED')
    def test_private_admin_target_has_no_redirect_or_userinfo(self):
        from admin import Admin
        for url in ('http://localhost','https://user:password@localhost','https://localhost/path','https://localhost?override=true'):
            with self.assertRaises(ValueError): Admin(url,'/not-used')
    def test_audit_policy_is_explicit(self):
        for retention in (0,-1,True):
            with self.assertRaises(ValueError): realm_policy('isolated-test','https://bff.synthetic.test/bff/callback',1,'synthetic','test',retention)
        policy=self.policy();self.assertTrue(policy['eventsEnabled']);self.assertTrue(policy['adminEventsEnabled']);self.assertFalse(policy['adminEventsDetailsEnabled']);self.assertEqual(policy['eventsExpiration'],3600);self.assertEqual(policy['attributes']['adminEventsExpiration'],'3600')
    def test_private_file_permissions_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);source=root/'dump';key=root/'key';source.write_bytes(b'test');key.write_bytes(b'x'*32)
            source.chmod(0o644);key.chmod(0o600)
            with self.assertRaises(ValueError): encrypt(source,root/'encrypted',key,'synthetic')
