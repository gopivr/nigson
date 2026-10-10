"""Approved NGN baseline and actual adapter minor-unit/market native qualification."""
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
    main(java_tests='NigeriaBaselineTest,NigeriaKoboTest,MarketValuesTest,MarketPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/ngn001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: approved disabled NG/Nigeria/NGN2 baseline, exact real HTTP Paystack kobo and current market guards; genuine provider/business activation not implied')
