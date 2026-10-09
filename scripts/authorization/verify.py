"""Native owner migrations, authorization races and HTTP contracts; preserves local state."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main
if __name__ == '__main__':
    main(java_tests='StaffAuthorizationTest,StaffValuesTest,JdbcSessionTrustTest,SecurityAssemblyTest,SecurityProtocolTest,KmsIdentityTest,HttpTrustBoundaryTest',
         evidence=ROOT / '.build/com-autho001/database-evidence', run_python=False,
         completion='PASS: COM-AUTHO-001 native PostgreSQL role/replay/race/audit/session/HTTP and current organization authority checks; original local state preserved')
