"""Native carrier/account binding and dependent financial/fulfillment acceptance."""
from pathlib import Path
from contextlib import contextmanager
import importlib.util
ROOT=Path(__file__).resolve().parents[2]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path)
    value=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value
carrier=module('carrier_qualification','scripts/carrier/verify.py')
payment=module('payment_qualification','scripts/payment/verify.py')
@contextmanager
def fixture(env):
    env['NIGSON_TEST_FULFILLMENT']='true'
    with carrier.fixture(env) as storage, payment.payment_fixture(env) as money:
        yield {**storage,**{key:value for key,value in money.items() if key not in {'NIGSON_TEST_ORDERS','NIGSON_TEST_PAYMENTS'}}}
if __name__=='__main__':
    carrier.main(java_tests='FedExAdapterTest,CarrierTrackingValuesTest,CarrierValuesTest,CarrierContextClientTest,CarrierShipmentRequestTest,CarrierLabelCodecTest,CarrierLabelStorageAcceptanceTest,CarrierBookingIntakePersistenceTest,CarrierBookingExecutionPersistenceTest,CarrierContextPersistenceTest,CarrierReconciliationPersistenceTest,CarrierCancellationPersistenceTest,ShipmentTrackingPersistenceTest,CarrierResultValuesTest,CarrierResultStatesTest,CarrierResultPersistenceTest,CarrierQuotePersistenceTest,CarrierQuoteHttpTest,HttpCarrierRatesTest,HttpCarrierReconciliationTest,HttpCarrierCancellationTest,CarrierCancellationResultClientTest,CarrierRateBindingTest,SecurityAssemblyTest,FulfillmentValuesTest,ShipmentPersistenceTest,PricingPersistenceTest,PricingValuesTest,OrderPersistenceTest,PaymentPersistenceTest,BuildCompatibilityTest',evidence=ROOT/'.build/carrier-account/database-evidence',run_python=False,java_environment=fixture,completion='PASS: immutable private account binding and dependent native carrier/financial/fulfillment checks; genuine account/route/deployment qualification NOT_RUN')
