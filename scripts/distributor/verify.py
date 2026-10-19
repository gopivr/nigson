#!/usr/bin/env python3
"""EXP-DISTR-001 PostgreSQL and actual Experience HTTP acceptance."""
from pathlib import Path
import importlib.util,os,sys
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'scripts/platform'))
spec=importlib.util.spec_from_file_location('platform_qualification',ROOT/'scripts/platform/verify.py');platform=importlib.util.module_from_spec(spec);spec.loader.exec_module(platform)
if __name__=='__main__':
    if os.environ.get('ENV','test')!='test':raise SystemExit('distributor qualification requires ENV=test')
    maven=os.environ.get('NIGSON_MAVEN_BIN')
    if maven:
        real_run=platform.subprocess.run
        def pinned(args,*a,**kw):
            if args and str(args[0]).endswith('mvnw'):
                args=[maven,*args[1:]];env={**os.environ,**kw.get('env',{})}
                if os.environ.get('NIGSON_JAVA_HOME'):
                    env['JAVA_HOME']=os.environ['NIGSON_JAVA_HOME'];env['PATH']=env['JAVA_HOME']+'/bin:'+env.get('PATH','')
                kw['env']=env
            return real_run(args,*a,**kw)
        platform.subprocess.run=pinned
    platform.main(java_tests='DistributorPersistenceTest,DistributorHttpTest,DistributorEntitlementHttpClientTest,BuildCompatibilityTest',run_python=False,evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-DISTR-001',completion='PASS: EXP-DISTR-001 Experience PostgreSQL and actual HTTP acceptance; API-117 exercised through stateful local mock, live Commerce API remains configuration gated')
