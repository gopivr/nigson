#!/usr/bin/env python3
"""Run EXP-CONSE-001 HTTP and owner-persistence acceptance on isolated PostgreSQL."""
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
        def pinned(args,*a,**kw):
            if args and str(args[0]).endswith('mvnw'):
                args=[maven,*args[1:]]
                kw['env']={**os.environ,**kw.get('env',{})}
                if os.environ.get('NIGSON_JAVA_HOME'):
                    kw['env']['JAVA_HOME']=os.environ['NIGSON_JAVA_HOME']
                    kw['env']['PATH']=os.environ['NIGSON_JAVA_HOME']+'/bin:'+kw['env'].get('PATH','')
            return real_run(args,*a,**kw)
        platform.subprocess.run=pinned
    if os.environ.get('ENV','test')!='test':raise SystemExit('consent qualification requires ENV=test')
    evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-CONSE-001'
    platform.main(java_tests='NewsletterPersistenceTest,PhoneConsentPersistenceTest,PhoneHttpTest,PhoneKeysTest,PhoneSecretsTest,BuildCompatibilityTest',run_python=False,evidence=evidence,
        completion='PASS: EXP-CONSE-001 newsletter double opt-in, stable private notification context, channel-purpose consent, current email/phone eligibility, one-time confirmation, idempotent unsubscribe, authoritative suppression, API-102 signed customer and stale-version denial; phone API-127/API-175 regression included')
