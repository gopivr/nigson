"""Native owned pricing/books and immutable carrier-bound checkout quote acceptance."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_PRICING']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='PricingValuesTest,PricingPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/pricing001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: native pricing books/tiers, exact minor units and immutable carrier-bound quotes, actual BFF/current authority/audit/races; commercial values and genuine provider activation not implied')
