"""Run MSG-001 signed inbound callback tests against isolated native PostgreSQL."""
from pathlib import Path
import importlib.util

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('messaging_inbound_native',ROOT/'scripts/platform/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

def java25(env):
    from contextlib import contextmanager
    @contextmanager
    def set_java():
        env['JAVA_HOME']='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
        env['PATH']='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin'
        yield {}
    return set_java()

if __name__=='__main__':
    module.main(java_tests='InboundMessageSecretsTest,InboundMessagePersistenceTest,PhoneSecretsTest,PhoneConsentPersistenceTest,PhoneHttpTest,StaffReplyPersistenceTest,AuditPersistenceTest,BuildCompatibilityTest',evidence=ROOT/'.build/messaging-inbound/database-evidence',run_python=False,java_environment=java25,completion='PASS: API-190 signed Twilio ingress, API-192/193 actor-bound durable WhatsApp reply, API-205 fresh suppression fence, encrypted context, stable dedup, UNKNOWN after uncertain provider acceptance, and owner commit checks; live sender/account qualification not implied')
