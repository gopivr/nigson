"""Qualify pinned ADOT startup offline with no network, credentials or application data."""
import json
import os
from pathlib import Path
import subprocess
import time
import uuid
ROOT=Path(__file__).resolve().parents[2]
IMAGE='public.ecr.aws/aws-observability/aws-otel-collector:v0.50.0@sha256:7968fb60db6a2390a47ba6a2df029745638486e285c9b2487da1b722d0855a3e'


def main():
 if os.environ.get('ENV','test') != 'test':raise ValueError('Offline collector qualification requires ENV=test')
 endpoint=json.loads(subprocess.run(['docker','context','inspect','--format','{{json .Endpoints.docker.Host}}'],capture_output=True,text=True,check=True).stdout)
 if not endpoint.startswith('unix://'):raise ValueError('Local Docker required')
 if os.environ.get('DOCKER_HOST') and os.environ['DOCKER_HOST']!=endpoint:raise ValueError('Local Docker required')
 config=(ROOT/'infrastructure/terraform/modules/observability/collector.yaml.tftpl').read_text()
 # Explicit non-deployable fixtures; these are not region or retention approvals.
 for name,value in {'region':'us-east-1','environment':'test','namespace':'Nigson/test','log_group':'/nigson/test/metrics'}.items():config=config.replace('${'+name+'}',value)
 path=ROOT/'.build/collector-qualification.yaml';path.parent.mkdir(exist_ok=True);path.write_text(config)
 name='nigson-test-otel-'+uuid.uuid4().hex[:10]
 try:
  subprocess.run(['docker','run','--detach','--name',name,'--network','none','--read-only','--tmpfs','/tmp','--user','10001:10001','--env','AWS_EC2_METADATA_DISABLED=true','--volume',str(path)+':/etc/otel.yaml:ro',IMAGE,'--config=file:/etc/otel.yaml','--set=service.telemetry.logs.level=info'],check=True,timeout=60,capture_output=True)
  for _ in range(30):
   logs=subprocess.run(['docker','logs',name],check=True,capture_output=True,text=True).stdout
   errors=subprocess.run(['docker','logs',name],check=True,capture_output=True,text=True).stderr
   output=logs+errors
   if 'Everything is ready' in output:
    print(output);break
   state=subprocess.run(['docker','inspect',name,'--format','{{.State.Running}}'],check=True,capture_output=True,text=True).stdout.strip()
   if state!='true':raise AssertionError(output)
   time.sleep(1)
  else:raise AssertionError('COLLECTOR_STARTUP_TIMEOUT')
  print('PASS: pinned ADOT ARM64 started bounded sampling/CloudWatch pipelines in a network-disabled container without credentials or application data; AWS delivery NOT_RUN')
 finally:
  subprocess.run(['docker','rm','--force',name],check=True,capture_output=True,timeout=60)
if __name__=='__main__':main()
