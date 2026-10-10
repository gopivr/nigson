"""Owned markets native persistence, policy/role/version guards and BFF assembly."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_MARKET']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='MarketValuesTest,MarketPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/market001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: owned market native persistence/policy/rollback/races and BFF assembly; genuine market activation not implied')
