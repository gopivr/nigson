"""Explicit genuine read-only qualification; never invokes mocks or creates external charges/sends."""
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
from provider_readiness import validate
try:
 validate(os.environ)
except ValueError as invalid:
 raise SystemExit("BLOCKED: " + str(invalid) + "; no provider call made") from None
subprocess.run([str(ROOT/'mvnw'),'-B','verify','dependency:build-classpath','-Dmdep.outputFile=target/provider-classpath.txt'],cwd=ROOT,check=True,timeout=300)
cp=(ROOT/'services/integration/target/provider-classpath.txt').read_text().strip()
cp=':'.join([str(ROOT/'.build/java'/o/'classes') for o in ('commerce','integration','common-events','common-security','common-observability','common-problem')])+':'+cp
target=ROOT/'tests/providers/target';target.mkdir(exist_ok=True)
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(target),str(ROOT/'tests/providers/src/com/nigson/providers/ProviderLiveQualification.java')],cwd=ROOT,check=True)
subprocess.run([str(java/'java'),'-cp',str(target)+':'+cp,'com.nigson.providers.ProviderLiveQualification'],cwd=ROOT,check=True,timeout=120)
