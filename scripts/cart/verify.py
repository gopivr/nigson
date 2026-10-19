"""Native owned cart BFF/persistence/atomicity/race acceptance."""
from pathlib import Path
from contextlib import contextmanager
import sys
import os
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_CART']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='CartValuesTest,CartPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/cart001/database-evidence',run_python=False,java_environment=fixture,maven_command=os.environ.get('NIGSON_MAVEN_BIN','./mvnw'),completion='PASS: native owned carts/current authority/context uniqueness/versioned lines/atomic audit/races and BFF; no stock promise/provider qualification implied')
