"""Native carrier implementation qualification; genuine account/route readiness is separate."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"scripts/platform"))
from verify import main
sys.path.insert(0,str(ROOT/"scripts/files"))
from storage_mock import fixture as storage_fixture
@contextmanager
def fixture(env):
 env["NIGSON_TEST_CARRIER"]="true"
 with storage_fixture(env) as storage:
  yield {"NIGSON_TEST_CARRIER_STORAGE_FIXTURE":storage["NIGSON_TEST_STORAGE_FIXTURE"]}
if __name__=="__main__":
 main(java_tests="FedExAdapterTest,CarrierValuesTest,CarrierContextClientTest,CarrierShipmentRequestTest,CarrierBookingIntakePersistenceTest,CarrierContextPersistenceTest,ShipmentTrackingPersistenceTest,CarrierResultValuesTest,CarrierResultStatesTest,CarrierResultPersistenceTest,CarrierQuotePersistenceTest,CarrierQuoteHttpTest,HttpCarrierRatesTest,CarrierRateBindingTest,SecurityAssemblyTest",evidence=ROOT/".build/carrier001/database-evidence",run_python=False,java_environment=fixture,completion="PASS: native carrier quotes/context/results/customer tracking/atomic EVT-027 intake and private creation input/Ship protocol; provider booking/label production/cancellation/polling composition and genuine account/release qualification NOT_RUN")
