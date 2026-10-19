#!/usr/bin/env python3
"""Run the native EVT-024 to durable Google Sheets mock acceptance suite."""
from pathlib import Path
import importlib.util, os, sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
spec=importlib.util.spec_from_file_location('platform_qualification',ROOT/'scripts/platform/verify.py')
platform=importlib.util.module_from_spec(spec);spec.loader.exec_module(platform)

if __name__=='__main__':
    maven=os.environ.get('NIGSON_MAVEN_BIN')
    if maven:
        real_run=platform.subprocess.run
        java_home=os.environ.get('NIGSON_JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home')
        def pinned(args,*a,**kw):
            if args and str(args[0]).endswith('mvnw'):
                args=[maven,*args[1:]]
                kw['env']={**os.environ,**kw.get('env',{}),'JAVA_HOME':java_home}
                kw['env']['PATH']=java_home+'/bin:'+kw['env'].get('PATH','')
            return real_run(args,*a,**kw)
        platform.subprocess.run=pinned
    if os.environ.get('ENV','test')!='test':raise SystemExit('career Sheet qualification requires ENV=test')
    platform.main(java_tests='CareerSheetSyncPersistenceTest,GoogleSheetsHttpAdapterTest,BuildCompatibilityTest',run_python=False,evidence=ROOT/'docs/implementation/evidence/2026-10-18-INT-PROVI-002',completion='PASS: EVT-024 stable job enqueue, Integration-owned Sheet row intent, formula-safe stable application row, OAuth JWT + Sheets HTTP local mock upsert, accepted-write timeout and UNKNOWN reconciliation; no live spreadsheet account/recipient policy qualification implied')
