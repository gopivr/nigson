"""Native PostgreSQL phone proof/consent checks; actual delivery is separately qualified."""
from pathlib import Path
import importlib.util
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('messaging_native',ROOT/'scripts/platform/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
if __name__=='__main__':
    module.main(java_tests='PhoneSecretsTest,PhoneConsentPersistenceTest,PhoneHttpTest,PhoneKeysTest,AuditPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/messaging/database-evidence',run_python=False,completion='PASS: BFF/private owned phone proof/consent/eligibility/suppression; provider worker/callback/live qualification not implied')
