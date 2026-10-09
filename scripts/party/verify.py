"""Commerce party native race/ownership/audit and actual Boot HTTP qualification."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
if __name__=='__main__':
    main(java_tests='PartyPersistenceTest,PartyValuesTest,SecurityAssemblyTest,StaffAuthorizationTest,JdbcSessionTrustTest',evidence=ROOT/'.build/party001/database-evidence',run_python=False,completion='PASS: COM-PARTY-001 profile/identity/default-address/replay/IDOR/country-policy/audit and current authorization; owner migrations and original local state preserved')
