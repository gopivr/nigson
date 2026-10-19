#!/usr/bin/env python3
"""Run the API-157 owner PostgreSQL and actual HTTP acceptance suite."""
from pathlib import Path
import importlib.util
import os
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
spec=importlib.util.spec_from_file_location('platform_qualification',ROOT/'scripts/platform/verify.py')
platform=importlib.util.module_from_spec(spec);spec.loader.exec_module(platform)

if __name__=='__main__':
    maven=os.environ.get('NIGSON_MAVEN_BIN')
    if maven:
        real_run=platform.subprocess.run
        def run_with_pinned_maven(args,*a,**kw):
            if args and str(args[0]).endswith('mvnw'):
                args=[maven,*args[1:]]
            return real_run(args,*a,**kw)
        platform.subprocess.run=run_with_pinned_maven
    if os.environ.get('ENV','test')!='test':
        raise SystemExit('experience file qualification requires ENV=test')
    evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-FILES-002'
    platform.main(java_tests='Career*Test',run_python=False,evidence=evidence,
                  completion='PASS: API-157 public anti-abuse checked CAREER_CV upload sessions, purpose/size/expiry bounds, encrypted idempotent capability replay, owner-local audit and actual anonymous HTTP route; no binary transfer or live object-store activation implied')
