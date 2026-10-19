#!/usr/bin/env python3
"""EXP-CONTE-002 actual HTTP and native PostgreSQL owner acceptance."""
from pathlib import Path
from contextlib import contextmanager
import os
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main as platform_main

@contextmanager
def java25(environment):
    java_home='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
    environment['JAVA_HOME']=java_home
    environment['PATH']=java_home+'/bin:'+environment.get('PATH','')
    yield {}

if __name__=='__main__':
    platform_main(java_tests='ContentKnowledgePersistenceTest,ContentKnowledgeHttpTest,BuildCompatibilityTest',
                  evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-CONTE-002',
                  run_python=False,maven_command=os.environ.get('NIGSON_MAVEN_BIN','./mvnw'),
                  java_projects='services/experience',
                  java_environment=java25,
                  completion='PASS: EXP-CONTE-002 API-125/126 Experience-owned approved public content validation and actual HTTP/native PostgreSQL acceptance; no cross-owner queries or direct events')
