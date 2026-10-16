"""Carrier native checks plus owned fulfillment/BFF handover compatibility acceptance."""
from pathlib import Path
from contextlib import contextmanager
import importlib.util
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('carrier_qualification',ROOT/'scripts/carrier/verify.py')
carrier=importlib.util.module_from_spec(spec)
spec.loader.exec_module(carrier)
@contextmanager
def fixture(env):
    env['NIGSON_TEST_FULFILLMENT']='true'
    with carrier.fixture(env) as extra:
        yield extra
if __name__=='__main__':
    carrier.main(java_tests='FulfillmentValuesTest,DeliveryRulePersistenceTest,ShipmentPersistenceTest,BuildCompatibilityTest,CarrierResultPersistenceTest,CarrierCancellationPersistenceTest,SecurityAssemblyTest',evidence=ROOT/'.build/carrier-handover/database-evidence',run_python=False,java_environment=fixture,completion='PASS: actual optional handover, original provider delivery scan, source stock/cancellation/authorization and prior reason-only compatibility qualified offline; genuine release qualification NOT_RUN')
