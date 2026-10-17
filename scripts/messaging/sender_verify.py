"""Fenced verification sends with actual PostgreSQL, SNS/SQS and stateful Twilio HTTP mocks."""
from pathlib import Path
import importlib.util
import subprocess
from contextlib import contextmanager
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('messaging_sender_native',ROOT/'scripts/platform/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
@contextmanager
def java25(env):
    env['JAVA_HOME']='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
    yield {}
if __name__=='__main__':
    subprocess.run(['python3',str(ROOT/'scripts/platform/build_mocks.py')],check=True)
    module.main(java_tests='VerificationSenderPersistenceTest,VerificationIntakePersistenceTest,PhoneSecretsTest,PhoneConsentPersistenceTest,PhoneHttpTest,PhoneKeysTest,AuditPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/messaging-sender/database-evidence',run_python=False,java_environment=java25,completion='PASS: fenced verification sender/intake and phone/BFF regression; signed callbacks/inbound/live qualification not implied')
