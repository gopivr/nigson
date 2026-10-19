#!/usr/bin/env python3
"""EXP-ENGAG-001 focused PostgreSQL and stateful Commerce mock acceptance."""
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
    args=[maven,*args[1:]];kw['env']={**os.environ,**kw.get('env',{})}
   return real_run(args,*a,**kw)
  platform.subprocess.run=pinned
 if os.environ.get('ENV','test')!='test':raise SystemExit('engagement qualification requires ENV=test')
 platform.main(java_tests='EngagementValuesTest,PurchaseProofClientTest,EngagementPersistenceTest,AuditPersistenceTest,BuildCompatibilityTest',run_python=False,evidence=ROOT/'docs/implementation/evidence/2026-10-18-EXP-ENGAG-001',completion='PASS: EXP-ENGAG-001 purchase-proofed reviews, moderation/version/outbox, public privacy filters and display-only campaigns on isolated owner PostgreSQL; Commerce HTTP is stateful local mock; live settings not implied')
