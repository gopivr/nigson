"""Commerce party native race/ownership/audit and actual Boot HTTP qualification."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_PARTY']='true'
    env['NIGSON_TEST_PRICING']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='PartyPersistenceTest,PartyValuesTest,SecurityAssemblyTest,StaffAuthorizationTest,JdbcSessionTrustTest',evidence=ROOT/'.build/party002/database-evidence',run_python=False,java_environment=fixture,completion='PASS: COM-PARTY-002 API-066 membership-scoped organization reads, entitlement projection, IDOR and actual BFF authentication; Commerce owner migrations and original local state preserved')
