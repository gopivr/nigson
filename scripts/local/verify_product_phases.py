#!/usr/bin/env python3
"""Fresh local BP2–BP5 acceptance on isolated BP5 schemas and stateful mocks.

Does not activate new live-product phases, deploy AWS or qualify real providers.
"""
from contextlib import contextmanager
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[2]
import sys
sys.path.insert(0, str(ROOT / 'scripts/platform'))
order = runpy.run_path(str(ROOT / 'scripts/platform/verify.py'))
paystack = runpy.run_path(str(ROOT / 'scripts/order/verify.py'))


@contextmanager
def fixtures(env):
    # These flags enable existing actual HTTP fixtures, not production success paths.
    # Reporting needs the stateful provider URL, without enabling the BP1
    # order HTTP assembly which replaces the catalogue's workload issuer.
    with paystack['fixture'](env) as provider:
        yield {'NIGSON_TEST_PARTY': 'true', 'NIGSON_TEST_PRICING': 'true',
               'NIGSON_TEST_CATALOGUE': '1',
               'NIGSON_TEST_ORDER_PAYSTACK': provider['NIGSON_TEST_ORDER_PAYSTACK']}


if __name__ == '__main__':
    order['main'](
        java_tests=','.join((
            'PromotionPersistenceTest', 'DistributorEntitlementPersistenceTest',
            'QuoteSkuEligibilityPersistenceTest', 'ReportPersistenceTest', 'ReportHttpTest',
            'AuditQueryPersistenceTest', 'EngagementPersistenceTest', 'EngagementValuesTest',
            'PhoneConsentPersistenceTest', 'CareerPersistenceTest', 'CareerValuesTest',
            'ContentKnowledgePersistenceTest', 'ContentKnowledgeHttpTest',
            'DistributorPersistenceTest', 'DistributorHttpTest', 'DistributorEntitlementHttpClientTest',
            'DistributorRelayTest', 'DistributorControllerTest', 'SecurityAssemblyTest',
            'CareerSheetSyncPersistenceTest', 'GoogleSheetsHttpAdapterTest')),
        java_environment=fixtures,
        python_tests=['services/ai/tests'],
        run_python=True,
        evidence=ROOT / '.build/product-phases/native-evidence',
        completion='PASS: selected BP2–BP5 domain persistence, actual HTTP and AI acceptance with isolated PostgreSQL/stateful mocks; live release/provider qualification not implied')
