#!/usr/bin/env python3
"""EXP-NOTIF-001 native migrations and API-137 signed HTTP acceptance."""
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
        java_tests='NotificationPersistenceTest,NotificationHttpTest,NewsletterPersistenceTest,PhoneConsentPersistenceTest,VerificationSenderPersistenceTest,NotificationReceiptPersistenceTest,BuildCompatibilityTest',
        run_python=False,
        evidence=ROOT/'docs/implementation/evidence/2026-10-19-EXP-NOTIF-001',
        completion='PASS: EXP-NOTIF-001 native PostgreSQL/API-137 owner outcome acceptance and consent/provider regression checks; Integration remains owner of escaped template rendering, durable provider attempts and live dispatch',
        maven_command=os.environ.get('NIGSON_MAVEN_BIN','mvn'),
        java_projects='services/experience,services/integration',
        java_environment=java25,
    )
