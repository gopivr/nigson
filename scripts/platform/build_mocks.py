"""Compile the test-only extension against the pinned standalone WireMock artifact."""
import os
import hashlib
import json
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
jar=ROOT/'.build/m2/org/wiremock/wiremock-standalone/3.13.2/wiremock-standalone-3.13.2.jar'
if not jar.is_file():
 subprocess.run([str(ROOT/'mvnw'),'-B','-N','dependency:get','-Dartifact=org.wiremock:wiremock-standalone:3.13.2'],cwd=ROOT,check=True)
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
 bin=Path(os.environ['JAVA_HOME'])/'bin'
 classes=target/('classes-'+source_hash);classes.mkdir(exist_ok=True)
 subprocess.run([str(bin/'javac'),'--release','17','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
 subprocess.run([str(bin/'jar'),'--create','--date=2026-10-04T00:00:00Z','--file',str(immutable),'-C',str(classes),'com'],check=True)
 checksum.write_text(hashlib.sha256(immutable.read_bytes()).hexdigest()+'\n')
# File bind mounts retain their immutable inode, even when a newer build is selected.
link=target/'provider-state.jar'
if link.is_symlink() and link.resolve()==immutable:pass
else:
 if link.exists() or link.is_symlink():link.unlink()
 link.symlink_to(immutable.name)
print('PASS: pinned immutable local mock extension')
