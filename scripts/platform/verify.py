"""Run native-JVM race tests against fresh PostgreSQL, preserving local state."""
import os
from pathlib import Path
import subprocess
import sys
import uuid
ROOT=Path(__file__).resolve().parents[2]
import importlib.util
sys.path.insert(0,str(ROOT/'scripts/database'))
_spec=importlib.util.spec_from_file_location('database_qualification',ROOT/'scripts/database/verify.py')
database_verify=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(database_verify)
Database,docker,preserved,OWNERS,IMAGE=(getattr(database_verify,name) for name in ('Database','docker','preserved','OWNERS','IMAGE'))
EVIDENCE=ROOT/'docs/implementation/evidence/2026-10-06-PLAT-001'
EVIDENCE.mkdir(parents=True,exist_ok=True)
database_verify.EVIDENCE=EVIDENCE

def main(java_tests="PlatformPersistenceTest,EventTransportTest,PaystackTransactionGuardTest", python_tests=None, evidence=None):
 global EVIDENCE
 if evidence is not None:
  EVIDENCE=evidence;EVIDENCE.mkdir(parents=True,exist_ok=True);database_verify.EVIDENCE=EVIDENCE
 if os.environ.get('ENV','test')!='test':raise ValueError('Qualification requires ENV=test')
 if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):raise ValueError('Local Docker required')
 if os.environ.get('DOCKER_CONTEXT','default') not in ('default','desktop-linux'):raise ValueError('Local Docker context required')
 endpoint=docker('context','inspect','--format','{{json .Endpoints.docker.Host}}')[0]
 import json
 if not json.loads(endpoint).startswith('unix://'):raise ValueError('Local Docker endpoint required')
 before=preserved();network='nigson-test-plat-'+uuid.uuid4().hex[:10];docker('network','create',network)
 db=Database.__new__(Database);db.network=network;db.name=network+'-postgres'
 try:
  docker('run','-d','--name',db.name,'--network',network,'--network-alias','postgres','-p','127.0.0.1::5432','-e','POSTGRES_USER=nigson_bootstrap','-e','POSTGRES_PASSWORD=mock-bootstrap','-e','POSTGRES_DB=nigson',IMAGE)
  import time
  for attempt in range(60):
   try:db.sql('SELECT 1');break
   except AssertionError:time.sleep(1)
  db.sql((ROOT/'infrastructure/docker/postgres/10-local-bootstrap.sql').read_text());db.sql('CREATE EXTENSION vector WITH SCHEMA ai')
  for owner in OWNERS:
   if owner!="ai":db.migrate(owner,1)
   db.migrate(owner,5);db.migrate(owner,5)
  port=docker('port',db.name,'5432/tcp')[0].split(':')[-1]
  stack=network+'-localstack'
  docker('run','-d','--name',stack,'--network',network,'--network-alias','localstack','-p','127.0.0.1::4566','-e','SERVICES=sns,sqs','-e','SQS_ENDPOINT_STRATEGY=path','-e','LOCALSTACK_HOST=127.0.0.1:4566','localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a')
  for attempt in range(60):
   try:docker('exec',stack,'awslocal','sns','list-topics');break
   except AssertionError:time.sleep(1)
  eventport=docker('port',stack,'4566/tcp')[0].split(':')[-1]
  env={**os.environ,'NIGSON_TEST_EVENT_ENDPOINT':'http://127.0.0.1:'+eventport,'NIGSON_TEST_JDBC_URL':'jdbc:postgresql://127.0.0.1:'+port+'/nigson','NIGSON_EVENT_FIXTURES':str(ROOT/'tests/platform/events')}
  subprocess.run(['./mvnw','-B','-pl','services/commerce,services/experience,services/integration','-am','-Dtest='+java_tests,'-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=ROOT,env=env,check=True,timeout=300)
  env['NIGSON_TEST_AI_DSN']='host=127.0.0.1 port='+port+' dbname=nigson user=nigson_ai_app password=mock-ai-runtime options=-csearch_path=ai'
  subprocess.run([str(ROOT/'.build/ai-env/bin/python'),'-m','pytest',*(python_tests or ['services/ai/tests/test_platform.py']),'-q'],cwd=ROOT,env=env,check=True,timeout=120)
  print('PASS: real PostgreSQL concurrent replay, canonical request hashes, rollback, consumer dedup and stale lease fencing for all Java owners')
 finally:
  docker('rm','-f','-v',network+'-localstack',db.name);docker('network','rm',network)
  assert before==preserved(),'Original local state changed'
if __name__=='__main__':main()
