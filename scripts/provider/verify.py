"""Isolated commerce job proxy: native owner persistence and actual private HTTP/KMS/JWT."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main

@contextmanager
def fixture(env):
    env['NIGSON_TEST_JOBS'] = 'true'
    yield {}

if __name__ == '__main__':
    main(java_tests='JobValuesTest,JobPersistenceTest,JobTransportTest,SecurityAssemblyTest,BuildCompatibilityTest',
         evidence=ROOT / '.build/provider001/database-evidence', run_python=False,
         java_environment=fixture,
         completion='PASS: commerce job proxy native persistence/compiled actual HTTP/JWT/KMS/MFA/UNKNOWN/dedup/audit/races; integration worker and genuine provider qualification not implied')
