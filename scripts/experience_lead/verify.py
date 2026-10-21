#!/usr/bin/env python3
"""EXP-LEAD-001 native PostgreSQL migrations and signed HTTP acceptance."""
from pathlib import Path
import os
import sys
from contextlib import contextmanager

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main as platform_main

@contextmanager
def java25(environment):
    home = os.environ.get('NIGSON_JAVA_HOME', '/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home')
    environment['JAVA_HOME'] = home
    environment['PATH'] = home + '/bin:' + environment.get('PATH', '')
    yield {}

if __name__ == '__main__':
    platform_main(
        java_tests='LeadPersistenceTest,LeadHttpTest,InboundMessagePersistenceTest,QuoteLeadPersistenceTest,QuoteLeadHttpTest,BuildCompatibilityTest',
        run_python=False,
        evidence=ROOT / 'docs/implementation/evidence/2026-10-19-EXP-LEAD-001',
        completion='PASS: EXP-LEAD-001 native Experience PostgreSQL migrations and actual HTTP lead intake, signed staff ownership, versioned assignment/history, durable EVT-016 and exact integration notification context; live recipient, anti-abuse provider and privacy configuration remain gated',
        maven_command=os.environ.get('NIGSON_MAVEN_BIN', 'mvn'),
        java_projects='services/experience',
        java_environment=java25,
    )
