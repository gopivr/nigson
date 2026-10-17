"""Signed Twilio status ingress and durable receipt tests against native owner DBs."""
from pathlib import Path
import importlib.util

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('messaging_callback_native',ROOT/'scripts/platform/verify.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

def java25(env):
    from contextlib import contextmanager
    @contextmanager
    def set_java():
        env['JAVA_HOME']='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
        yield {}
    return set_java()

if __name__=='__main__':
    module.main(java_tests='TwilioStatusIngressTest,NotificationReceiptPersistenceTest',evidence=ROOT/'.build/messaging-callback/database-evidence',run_python=False,java_environment=java25,completion='PASS: signed Twilio status ingress, minimized private receipt relay, durable SID binding/deduplication and owner-local monotonic projection; real callback reachability/live qualification not implied')
