"""Native integration-owned job management qualification; provider execution scope is separate."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
if __name__=='__main__':
 main(java_tests='PrivateHttpTest,IntegrationJobExecutionTest,IntegrationJobHttpTest,IntegrationJobPersistenceTest,JobValuesTest',evidence=ROOT/'.build/integration-provider001/database-evidence',run_python=False,completion='PASS: native private integration jobs/approved payment command HTTP/UNKNOWN lease and cursor recovery/atomic audit/inbox/SNS-SQS; genuine account/cloud and downstream carrier/Sheet/release qualification NOT_RUN')
