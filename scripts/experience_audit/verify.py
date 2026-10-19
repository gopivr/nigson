#!/usr/bin/env python3
"""EXP-AUDIT-001 native PostgreSQL and actual Experience HTTP acceptance."""
from pathlib import Path
import os,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main as platform_main

if __name__=='__main__':
    platform_main(java_tests='AuditPersistenceTest,AuditHttpTest,BuildCompatibilityTest',
                  evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-AUDIT-001',
                  run_python=False,maven_command=os.environ.get('NIGSON_MAVEN_BIN','./mvnw'),
                  completion='PASS: API-138 Experience-owned native PostgreSQL/actual HTTP acceptance; exact signed ADMIN workload/actor scope, filters, pagination, redaction, no-store and append-only read behavior')
