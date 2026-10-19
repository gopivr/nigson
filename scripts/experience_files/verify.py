#!/usr/bin/env python3
"""Fresh/repeat owner migrations and actual HTTP acceptance for EXP-FILES-001."""
from pathlib import Path
import os,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main as platform_main
from contextlib import contextmanager
@contextmanager
def java25(environment):
 home='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
 environment['JAVA_HOME']=home;environment['PATH']=home+'/bin:'+environment.get('PATH','')
 yield {}
if __name__=='__main__':
 platform_main(java_tests='ExperienceFileHttpTest,BuildCompatibilityTest',evidence=ROOT/'docs/implementation/evidence/2026-10-19-EXP-FILES-001',run_python=False,maven_command=os.environ.get('NIGSON_MAVEN_BIN','mvn'),java_projects='services/experience',java_environment=java25,completion='PASS: EXP-FILES-001 native PostgreSQL migrations and actual signed HTTP file lifecycle, ownership, replay, versioning, quarantine and UNKNOWN reconciliation')
