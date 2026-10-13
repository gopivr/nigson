"""Native carrier implementation qualification; genuine account/route readiness is separate."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"scripts/platform"))
from verify import main
@contextmanager
def fixture(env):
 env["NIGSON_TEST_CARRIER"]="true"
 yield {}
if __name__=="__main__":
 main(java_tests="FedExAdapterTest,CarrierValuesTest,CarrierContextPersistenceTest,CarrierQuotePersistenceTest,CarrierQuoteHttpTest,HttpCarrierRatesTest,CarrierRateBindingTest,SecurityAssemblyTest",evidence=ROOT/".build/carrier001/database-evidence",run_python=False,java_environment=fixture,completion="PASS: native private FedEx rate command, whole-response uncertainty, exact currency, audited durable acknowledgement and owned commerce binding/replay; booking/tracking composition and genuine account/release qualification NOT_RUN")
