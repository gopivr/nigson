"""Owned inventory native persistence, stock races and BFF assembly."""
from pathlib import Path
from contextlib import contextmanager
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
@contextmanager
def fixture(env):
    env['NIGSON_TEST_INVENTORY']='true'
    yield {}
if __name__=='__main__':
    main(java_tests='InventoryValuesTest,InventoryPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest', evidence=ROOT/'.build/inventory001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: owned inventory native persistence/rollback/races/stock accounting and application assembly; real initial stock/provider qualification not implied')
