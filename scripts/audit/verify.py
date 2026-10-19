"""Run COM-AUDIT-001 against fresh native Commerce PostgreSQL and real HTTP."""
from contextlib import contextmanager
from pathlib import Path
import os
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main


@contextmanager
def enable_audit_http(_env):
    yield {'NIGSON_TEST_AUDIT': 'true', 'NIGSON_EXPERIENCE_BASE_URL': 'http://127.0.0.1:9',
           'NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT': 'SYNTHETIC_DISTRIBUTOR_BUY'}


if __name__ == '__main__':
    main(java_tests='AuditQueryPersistenceTest,AuditPersistenceTest,SecurityAssemblyTest,SecurityProtocolTest,BuildCompatibilityTest',
         evidence=ROOT / 'docs/implementation/evidence/2026-10-18-COM-AUDIT-001',
         run_python=False,
         java_environment=enable_audit_http,
         maven_command=os.environ.get('NIGSON_MAVEN_BIN', './mvnw'),
         completion='PASS: COM-AUDIT-001 native Commerce migrations, append-only PostgreSQL ownership, repeatable-read filters, current ADMIN checks, redaction, safe failure, concurrent GET/append and actual BFF HTTP acceptance; no retention or access policy inferred')
