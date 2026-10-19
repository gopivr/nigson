"""Native Commerce reporting acceptance using the established local order fixture."""
from pathlib import Path
import os
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main as platform_main
import importlib.util
spec=importlib.util.spec_from_file_location('order_verification_fixture',ROOT/'scripts/order/verify.py')
order_verify=importlib.util.module_from_spec(spec);spec.loader.exec_module(order_verify)

if __name__=='__main__':
    platform_main(java_tests='ReportPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',
                  evidence=ROOT/'.build/reporting001/native-evidence',run_python=False,
                  java_environment=order_verify.fixture,maven_command=os.environ.get('NIGSON_MAVEN_BIN','./mvnw'),
                  completion='PASS: API-075 native Commerce PostgreSQL/actual BFF date boundaries, successful refund totals, ANALYST/current-role and organization scope, no-store, bounded queries and safe validation; live release gates not implied')
