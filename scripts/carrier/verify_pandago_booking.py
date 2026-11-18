"""Isolated native PostgreSQL + mock-only booking acceptance. Never calls PandaGo stage/live."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
# Load order's stateful Paystack fixture without shadowing platform.verify.
import importlib.util
spec=importlib.util.spec_from_file_location('order_fixture',ROOT/'scripts/order/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
if __name__=='__main__':
 main(java_tests='OrderValuesTest,OrderPersistenceTest,PandagoBookingPersistenceTest,PandagoAdapterTest,PandagoCommerceClientTest,CarrierResultValuesTest,CarrierResultStatesTest,BuildCompatibilityTest',evidence=ROOT/'.build/pandago-booking003/database-evidence',run_python=False,java_environment=module.fixture,completion='PASS: isolated automatic prepaid/COD shipment intents, durable PandaGo booking/UNKNOWN safety and provider payload tests')
