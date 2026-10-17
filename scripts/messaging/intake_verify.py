"""Owned verification notification intake and phone/BFF regression with real PostgreSQL/SNS/SQS."""
from pathlib import Path
import importlib.util
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('messaging_intake_native',ROOT/'scripts/platform/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
if __name__=='__main__':
    module.main(java_tests='VerificationIntakePersistenceTest,PhoneSecretsTest,PhoneConsentPersistenceTest,PhoneHttpTest,PhoneKeysTest,AuditPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/messaging-intake/database-evidence',run_python=False,completion='PASS: paired durable verification intake and phone/BFF regression; provider dispatch/status/inbound/live qualification not implied')
