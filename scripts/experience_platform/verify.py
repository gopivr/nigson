#!/usr/bin/env python3
"""EXP-PLATF-001 fresh/repeat migrations and native API-139 acceptance."""
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
        java_tests='ConfigurationHttpTest,PlatformPersistenceTest,AuditHttpTest,AuditPersistenceTest,NotificationHttpTest,NotificationPersistenceTest,MetricPolicyTest,BuildCompatibilityTest',
        run_python=False,
        evidence=ROOT/'docs/implementation/evidence/2026-10-20-EXP-PLATF-001',
        completion='PASS: EXP-PLATF-001 native Experience PostgreSQL/API-139 signed HTTP configuration, immutable revision, idempotency race, queue replay, worker controls, audit/redaction and existing platform/audit/notification regressions',
        maven_command=os.environ.get('NIGSON_MAVEN_BIN','mvn'),
        java_projects='services/experience',
        java_environment=java25,
    )
