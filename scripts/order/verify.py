"""Isolated owned order T1/T3 and actual stateful Paystack HTTP qualification."""
from pathlib import Path
from contextlib import contextmanager
import subprocess
import sys
import time
import urllib.request
import uuid
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main,docker

@contextmanager
def fixture(env):
    subprocess.run([sys.executable,str(ROOT/'scripts/platform/build_mocks.py')],cwd=ROOT,env=env,check=True,timeout=120)
    name='nigson-test-order-paystack-'+uuid.uuid4().hex[:10]
    extension=(ROOT/'tests/mocks/target/provider-state.jar').resolve()
    try:
        docker('run','-d','--name',name,'-p','127.0.0.1::8080','-e','APP_ENV=test','-e','MOCK_PROVIDER=paystack','--mount',f'type=bind,source={extension},target=/var/wiremock/extensions/provider-state.jar,readonly','wiremock/wiremock:3.13.2@sha256:0d4ecb3e4dc8213fd7a4d37d6a78f6e6b553a6d2e15bd51b0999781282ac61b3','--extensions','com.nigson.mocks.ProviderStateExtension','--no-request-journal','--disable-request-logging')
        port=docker('port',name,'8080/tcp')[0].split(':')[-1]
        base='http://127.0.0.1:'+port
        for attempt in range(60):
            try:
                with urllib.request.urlopen(base+'/__scenario/state',timeout=2) as response:
                    if response.status==200:break
            except (OSError,TimeoutError):time.sleep(1)
        else:raise AssertionError('Stateful Paystack mock unavailable')
        yield {'NIGSON_TEST_ORDER_PAYSTACK':base,'NIGSON_TEST_ORDERS':'true'}
    finally:
        docker('rm','-f','-v',name)

if __name__=='__main__':
    main(java_tests='OrderValuesTest,OrderPersistenceTest,SecurityAssemblyTest,BuildCompatibilityTest',evidence=ROOT/'.build/order001/database-evidence',run_python=False,java_environment=fixture,completion='PASS: native atomic checkout/frozen orders/current authority/verified stock lifecycle and actual stateful Paystack HTTP; genuine provider and release qualification not implied')
