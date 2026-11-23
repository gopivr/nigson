"""Isolated cancellation acceptance; never calls real carrier/payment endpoints."""
from pathlib import Path
import sys, importlib.util
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
spec=importlib.util.spec_from_file_location('order_fixture',ROOT/'scripts/order/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
if __name__=='__main__':
 main(java_tests='OrderCancellationPersistenceTest,OrderPersistenceTest,PaymentPersistenceTest,PandagoCancellationPersistenceTest,PandagoBookingPersistenceTest,PandagoAdapterTest,HttpCarrierCancellationTest,OrderValuesTest,SecurityAssemblyTest',evidence=ROOT/'.build/order-cancellation/database-evidence',run_python=False,java_environment=module.fixture,completion='PASS: isolated native cancellation, race, provider and regression checks')
