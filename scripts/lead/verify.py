#!/usr/bin/env python3
"""EXP-LEAD-002 native PostgreSQL and actual HTTP acceptance."""
from pathlib import Path
import importlib.util
import os
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
spec = importlib.util.spec_from_file_location('platform_qualification', ROOT / 'scripts/platform/verify.py')
platform = importlib.util.module_from_spec(spec)
spec.loader.exec_module(platform)

if __name__ == '__main__':
    if os.environ.get('ENV', 'test') != 'test':
        raise SystemExit('lead qualification requires ENV=test')
    maven = os.environ.get('NIGSON_MAVEN_BIN')
    if maven:
        real_run = platform.subprocess.run
        def pinned(args, *a, **kw):
            if args and str(args[0]).endswith('mvnw'):
                args = [maven, *args[1:]]
                env = {**os.environ, **kw.get('env', {})}
                if os.environ.get('NIGSON_JAVA_HOME'):
                    env['JAVA_HOME'] = os.environ['NIGSON_JAVA_HOME']
                    env['PATH'] = env['JAVA_HOME'] + '/bin:' + env.get('PATH', '')
                kw['env'] = env
            return real_run(args, *a, **kw)
        platform.subprocess.run = pinned
    platform.main(
        java_tests='QuoteLeadPersistenceTest,QuoteLeadHttpTest,QuoteSkuEligibilityHttpClientTest,QuoteSkuEligibilityPersistenceTest,BuildCompatibilityTest',
        run_python=False,
        evidence=ROOT / 'docs/implementation/evidence/2026-10-18-EXP-LEAD-002',
        completion='PASS: EXP-LEAD-002 Experience lead PostgreSQL and actual HTTP acceptance; private API-206 adapter exercised through an explicit stateful local mock; live recipient and Commerce workload configuration remain gated',
    )
