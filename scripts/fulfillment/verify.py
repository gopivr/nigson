"""Native owned delivery/shipment conservation qualification."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_FULFILLMENT']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='FulfillmentValuesTest,DeliveryRulePersistenceTest,ShipmentPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/fulfillment001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: native owned delivery rules, all six BFF APIs/current authority and shipment/stock conservation/receipt/duplicate/races; genuine carrier qualification and release composition not implied')
