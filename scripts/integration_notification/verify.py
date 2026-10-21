#!/usr/bin/env python3
"""INT-NOTIF-001 fresh/repeat owner migrations and signed HTTP/provider regressions."""
from pathlib import Path
import os
import sys
from contextlib import contextmanager

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main as platform_main

@contextmanager
def java25(environment):
    home=os.environ.get('NIGSON_JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home')
    environment['JAVA_HOME']=home
    environment['PATH']=home+'/bin:'+environment.get('PATH','')
    yield {}

if __name__=='__main__':
    platform_main(
        java_tests=os.environ.get('NIGSON_JAVA_TESTS','NotificationTemplateValuesTest,NotificationTemplateHttpTest,VerificationIntakePersistenceTest,VerificationSenderPersistenceTest,NotificationReceiptPersistenceTest,BuildCompatibilityTest'),
        run_python=False,
        evidence=ROOT/'docs/implementation/evidence/2026-10-20-INT-NOTIF-001',
        completion='PASS: INT-NOTIF-001 Integration-owned API-142 signed HTTP template revision/idempotency/race acceptance plus fresh-consent, suppression, durable attempt, callback outcome and UNKNOWN reconciliation regressions',
        maven_command=os.environ.get('NIGSON_MAVEN_BIN','mvn'),
        java_projects='services/integration',
        java_environment=java25,
    )
