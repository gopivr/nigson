#!/usr/bin/env python3
"""Isolated financial callback regression plus stateful partner HTTP ports."""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[2]
payment = runpy.run_path(str(ROOT / 'scripts/payment/verify.py'))

if __name__ == '__main__':
    payment['fixture'].main(
        java_tests='PaymentReceiptKeysTest,PaymentActivationTest,PaymentValuesTest,PaymentPayloadCipherTest,PaystackWebhookProtocolTest,PaymentPersistenceTest,OrderPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest,PaystackModeTest,PaystackTransactionGuardTest,PartnerAdaptersTest',
        evidence=ROOT / '.build/partners/native-evidence',
        run_python=False,
        java_environment=payment['payment_fixture'],
        completion='PASS: isolated native payment/refund/UNKNOWN and signed callback alias checks plus stateful Termii HTTP protocol tests; partner workflow activation and production deployment not implied')
