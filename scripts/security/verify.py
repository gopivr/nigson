"""SEC-001 native owner-state/race qualification; preserves nigson-local resources."""
from pathlib import Path
import sys
import os
import subprocess
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main
def identity(env):
    if os.environ.get('SEC_IDENTITY_CHECK') == 'true':
        subprocess.run(['make', 'identity-check', 'UV=' + os.environ.get('UV', '.build/uv-package/bin/uv')], cwd=ROOT, env={**env, 'NIGSON_SEC_IDENTITY': 'true'}, check=True, timeout=600)

if __name__ == '__main__':
    main(java_tests='JdbcSessionTrustTest,SecurityProtocolTest,KmsIdentityTest,HttpTrustBoundaryTest,SecurityAssemblyTest',
         python_tests=['services/ai/tests/test_identity.py'],
         evidence=ROOT / '.build/sec001/database-evidence', run_python=os.environ.get('PROFILE') == 'ai',
         qualification=identity, completion='PASS: SEC-001 native JDBC one-use state/race, rotation, idle/absolute TTL, encrypted Spring Session compatibility, current commerce policy/audit/revocation and KMS/protocol checks')
