"""Execute a pinned Testcontainers provider qualification harness on local Docker."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
if os.environ.get('MODE','mock')!='mock':raise ValueError('Offline contract checks require MODE=mock; genuine checks use provider-live-check')
if os.environ.get('ENV','test')!='test':raise ValueError('Provider qualification requires ENV=test')
if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):raise ValueError('Local Docker required')
endpoint=subprocess.run(['docker','context','inspect','--format','{{json .Endpoints.docker.Host}}'],capture_output=True,text=True,check=True).stdout
if not json.loads(endpoint).startswith('unix://'):raise ValueError('Local Docker endpoint required')
spec=importlib.util.spec_from_file_location('data_verify',ROOT/'scripts/database/verify.py')
# Database helper imports its own colocated catalog.
import sys
sys.path.insert(0,str(ROOT/'scripts/database'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
os.environ['DOCKER_HOST']=json.loads(endpoint)
os.environ['TESTCONTAINERS_TINYIMAGE_CONTAINER_IMAGE']='alpine:3.17@sha256:8fc3dacfb6d69da8d44e42390de777e48577085db99aa4e4af35f483eb08b989'
os.environ['TESTCONTAINERS_RYUK_CONTAINER_IMAGE']='testcontainers/ryuk:0.14.0@sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0'
before=module.preserved()
try:
 subprocess.run([sys.executable,str(ROOT/'scripts/platform/build_mocks.py')],check=True)
 subprocess.run([str(ROOT/'mvnw'),'-B','verify','dependency:build-classpath','-Dmdep.outputFile=target/provider-classpath.txt'],cwd=ROOT,check=True,timeout=300)
 cp=(ROOT/'services/integration/target/provider-classpath.txt').read_text().strip()
 cp=':'.join([str(ROOT/'.build/java'/owner/'classes') for owner in ('commerce','integration','common-events','common-security')])+':'+cp
 target=ROOT/'tests/providers/target';target.mkdir(exist_ok=True)
 java=Path(os.environ['JAVA_HOME'])/'bin'
 subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(target),*map(str,(ROOT/'tests/providers/src').rglob('*.java'))],cwd=ROOT,check=True)
 subprocess.run([str(java/'java'),'-cp',str(target)+':'+cp,'com.nigson.providers.ProviderQualification'],cwd=ROOT,check=True,timeout=300)
finally:
 assert before==module.preserved(),'Original local state changed'
