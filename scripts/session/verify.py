from pathlib import Path
import sys
import os
import subprocess
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
def vendor(env):
 if os.environ.get('NIGSON_SESSION_VENDOR')=='true':
  subprocess.run(['make','identity-check','UV='+os.environ.get('UV','.build/uv-package/bin/uv')],cwd=ROOT,env={**env,'NIGSON_SEC_IDENTITY':'true','NIGSON_IDENTITY_EVIDENCE':str(ROOT/'docs/implementation/evidence/2026-10-09-COM-SESSI-001/vendor')},check=True,timeout=600)
if __name__=='__main__':
 main(java_tests='SessionHttpTest,JdbcSessionTrustTest,SecurityProtocolTest,SecurityAssemblyTest,PartyPersistenceTest',evidence=ROOT/'.build/session001/database-evidence',run_python=False,qualification=vendor,completion='PASS: COM-SESSI-001 assembled session endpoints, HTTP OIDC/code/PKCE/nonce/state/replay/logout, refresh/replica/TTL and current Party; migrations and original local state preserved')
