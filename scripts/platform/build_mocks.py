"""Compile the test-only extension against the pinned standalone WireMock artifact."""
import os
import hashlib
import json
import shutil
import sys
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
# Use mvnw.cmd on Windows; the Unix shell script is not a valid Win32 application.
_mvnw = ROOT/('mvnw.cmd' if sys.platform == 'win32' else 'mvnw')
jar=ROOT/'.build/m2/org/wiremock/wiremock-standalone/3.13.2/wiremock-standalone-3.13.2.jar'
if not jar.is_file():
 mvn=str(ROOT/'mvnw.cmd' if sys.platform=='win32' else ROOT/'mvnw')
 subprocess.run([mvn,'-B','-N','dependency:get','-Dartifact=org.wiremock:wiremock-standalone:3.13.2'],cwd=ROOT,check=True)
expected=json.loads((ROOT/'docs/dependencies/mock-lock.json').read_text())['wiremock_standalone']['sha256']
if hashlib.sha256(jar.read_bytes()).hexdigest()!=expected:raise ValueError('Pinned WireMock artifact checksum mismatch')
target=ROOT/'tests/mocks/target';target.mkdir(exist_ok=True)
sources=sorted((ROOT/'tests/mocks/src').rglob('*.java'))
source_hash=hashlib.sha256(expected.encode()+b''.join(p.relative_to(ROOT).as_posix().encode()+p.read_bytes() for p in sources)).hexdigest()
immutable=target/('provider-state-'+source_hash+'.jar')
checksum=immutable.with_suffix('.sha256')
if immutable.exists():
 if not checksum.exists() or hashlib.sha256(immutable.read_bytes()).hexdigest()!=checksum.read_text().strip():raise ValueError('Cached extension checksum mismatch')
else:
 # Resolve javac/jar from JAVA_HOME if set, otherwise fall back to PATH.
 _java_home = os.environ.get('JAVA_HOME')
 if _java_home:
  _jbin = Path(_java_home)/'bin'
  _javac = str(_jbin/'javac')
  _jar_cmd = str(_jbin/'jar')
 else:
  _javac = shutil.which('javac') or 'javac'
  _jar_cmd = shutil.which('jar') or 'jar'
 classes=target/('classes-'+source_hash);classes.mkdir(exist_ok=True)
 subprocess.run([_javac,'--release','17','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
 subprocess.run([_jar_cmd,'--create','--date=2026-10-04T00:00:00Z','--file',str(immutable),'-C',str(classes),'com'],check=True)
 checksum.write_text(hashlib.sha256(immutable.read_bytes()).hexdigest()+'\n')
# On Windows, symlinks require Developer Mode or admin; use a plain copy instead.
link=target/'provider-state.jar'
if link.is_symlink() and link.resolve()==immutable:pass
elif link.exists() and hashlib.sha256(link.read_bytes()).hexdigest()==checksum.read_text().strip():pass
else:
 if link.exists() or link.is_symlink():link.unlink()
 try:
  link.symlink_to(immutable.name)
 except OSError:
  shutil.copy2(immutable, link)
print('PASS: pinned immutable local mock extension')
