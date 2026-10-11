"""Isolated payment persistence and real stateful Paystack HTTP acceptance."""
from pathlib import Path
import importlib.util
from contextlib import contextmanager

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("order_fixture", ROOT / "scripts/order/verify.py")
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)

@contextmanager
def payment_fixture(env):
    with fixture.fixture(env) as values:
        yield {**values, "NIGSON_TEST_PAYMENTS": "true"}

if __name__ == "__main__":
    fixture.main(
        java_tests="PaymentReceiptKeysTest,PaymentActivationTest,PaymentValuesTest,PaymentPayloadCipherTest,PaystackWebhookProtocolTest,PaymentPersistenceTest,OrderPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest",
        evidence=ROOT / ".build/payment001/database-evidence",
        run_python=False,
        java_environment=payment_fixture,
        completion="PASS: scoped financial values/encrypted receipts/refund reservations/actual refund HTTP and unknown reconciliation with accepted order regression; complete payment API/callback/scheduler/release qualification not implied",
    )
