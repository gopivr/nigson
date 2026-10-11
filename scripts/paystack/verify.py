"""Exclusive Paystack native qualification with accepted owned payment and order regressions."""
from pathlib import Path
import importlib.util
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('payment_fixture', ROOT/'scripts/payment/verify.py')
payment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(payment)
if __name__ == '__main__':
    payment.fixture.main(
        java_tests='PaystackModeTest,PaymentReceiptKeysTest,PaymentActivationTest,PaymentValuesTest,PaymentPayloadCipherTest,PaystackWebhookProtocolTest,PaymentPersistenceTest,OrderPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',
        evidence=ROOT/'.build/paystack001/database-evidence', run_python=False,
        java_environment=payment.payment_fixture,
        completion='PASS: AC-PAYSTACK-001 native stateful HTTP checkout/refunds/signed durable callbacks/once-only money/UNKNOWN/NGN and mode isolation; genuine provider/account and BP1 release qualification NOT_RUN',
    )
