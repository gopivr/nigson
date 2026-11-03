#!/usr/bin/env python3
"""Seed synthetic, deterministic local fixtures without deleting existing records.

Only installed application tables are seeded. No migrations, identity changes,
provider calls or production configuration changes are performed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
TABLES = json.loads((ROOT / 'database/schema-manifest.json').read_text())['tables']
NAMESPACE = uuid.UUID('4602c590-2f1f-4e96-9a72-c03cde0e88ed')


def uid(key):
    return str(uuid.uuid5(NAMESPACE, 'nigson-demo-v1/' + key))


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (dict, list)):
        value = json.dumps(value)
    return "'" + str(value).replace("'", "''") + "'"


# Terminal states prevent schedulers from dispatching synthetic work. Commercial
# configuration remains disabled; amounts are explicitly test fixtures in NGN.
OVERRIDES = {
    'party': {'email': 'demo.customer@example.invalid', 'display_name': 'Demo Customer',
              'identity_subject': 'demo-only-no-login', 'identity_issuer': 'https://identity.example.invalid'},
    'currency': {'code': 'NGN', 'minor_unit': 2, 'active': True},
    'market': {'code': 'DEMO_NG', 'name': 'Demo Nigeria (testing only)', 'enabled': False},
    'product': {'name': 'Demo Solar Panel', 'slug': 'demo-solar-panel',
                'description': 'Synthetic 400W solar panel for local testing.', 'status': 'DRAFT'},
    'brand': {'name': 'Demo Energy', 'slug': 'demo-energy'},
    'category': {'name': 'Demo Solar Equipment', 'slug': 'demo-solar-equipment'},
    'sku': {'code': 'DEMO-SOLAR-400', 'variant_name': 'Demo 400W', 'unit': 'unit'},
    'price_book': {'code': 'DEMO-NGN-RETAIL', 'active': False},
    'price_entry': {'unit_amount': 125000},
    'stock_location': {'code': 'DEMO-LAGOS', 'name': 'Demo Lagos Warehouse'},
    'inventory_balance': {'on_hand': 25, 'reserved': 0, 'allocated': 0},
    'inventory_movement': {'movement_type': 'ADJUST', 'delta_on_hand': 25, 'delta_reserved': 0, 'delta_allocated': 0},
    'inventory_reservation': {'status': 'RELEASED'},
    'address': {'recipient': 'Demo Customer', 'phone': '+2340000000000',
                'line1': '1 Demo Street', 'city': 'Lagos', 'country_code': 'NG'},
    'delivery_rule': {'fee': 0, 'active': False},
    'cart': {'status': 'EXPIRED'},
    'carrier_quote': {'provider': 'FEDEX', 'mode': 'MOCK', 'status': 'EXPIRED', 'delivery_amount': 0},
    'sales_order': {'order_number': 'DEMO-ORDER-001', 'status': 'CANCELLED', 'payment_status': 'UNPAID',
                    'fulfillment_status': 'CANCELLED', 'address_snapshot': {'label': 'Demo address', 'recipient': 'Demo Customer', 'phone': '+2340000000000', 'line1': '1 Demo Street', 'line2': None, 'city': 'Lagos', 'region': None, 'postalCode': None, 'countryCode': 'NG', 'isDefault': False}, 'subtotal': 125000, 'discount_total': 0,
                    'tax_total': 0, 'delivery_total': 0, 'grand_total': 125000},
    'order_line': {'sku_code': 'DEMO-SOLAR-400', 'product_name': 'Demo Solar Panel', 'unit': 'unit',
                   'quantity': 1, 'unit_amount': 125000, 'discount_amount': 0, 'tax_amount': 0, 'line_total': 125000},
    'order_total_component': {'component_type': 'SUBTOTAL', 'amount': 125000},
    'order_status_history': {'new_status': 'CANCELLED', 'reason': 'Synthetic cancelled order for testing'},
    'payment_attempt': {'provider': 'PAYSTACK', 'state': 'FAILED', 'expected_amount': 125000},
    'payment_transaction': {'settled_amount': 0, 'provider_status': 'failed'},
    'refund': {'state': 'REJECTED', 'amount': 1, 'reason': 'Demo rejection: no settled payment'},
    'refund_line': {'amount': 0},
    'shipment': {'provider': 'FEDEX', 'status': 'CANCELLED'},
    'shipment_event': {'source': 'DEMO', 'status': 'CANCELLED'},
    'carrier_booking': {'provider': 'FEDEX', 'mode': 'MOCK', 'state': 'CANCELLED'},
    'carrier_cancellation': {'mode': 'MOCK', 'state': 'CANCELLED'},
    'outbox_event': {'state': 'PUBLISHED', 'event_type': 'demo.fixture'},
    'processed_event': {'outcome': 'IGNORED', 'event_type': 'demo.fixture'},
    'idempotency_record': {'state': 'SUCCEEDED', 'response_status': 200, 'response_body': {'demo': True}},
    'audit_record': {'actor_kind': 'SYSTEM', 'action': 'DEMO_SEED', 'resource_type': 'demo_fixture'},
    'feature_configuration': {'active': False, 'value': {'demo': True}},
    'file_transfer': {'state': 'REJECTED'},
    'asset_reference': {'status': 'REJECTED', 'mime_type': 'image/png'},
    'file_asset': {'scan_status': 'REJECTED', 'mime_type': 'application/pdf'},
    'staff_role_assignment': {'role': 'ANALYST', 'revoked_at': '2026-01-01T00:00:00Z'},
    'webhook_receipt': {'provider': 'PAYSTACK', 'state': 'REJECTED', 'verified_signature': False},
    'notification_job': {'state': 'SUPPRESSED', 'channel': 'EMAIL', 'classification': 'TRANSACTIONAL',
                         'template_code': 'demo-notification', 'template_revision': 1},
    'notification_template': {'code': 'demo-notification', 'active': False, 'body': 'Demo notification. No delivery.', 'locale': 'en'},
    'notification_dispatch': {'stage': 'SUPPRESSED', 'account_sid': 'AC' + '0' * 32,
                              'messaging_service_sid': 'MG' + '0' * 32},
    'notification_attempt': {'result': 'SUPPRESSED', 'provider': 'DEMO'},
    'message_receipt': {'provider': 'DEMO', 'provider_status': 'SUPPRESSED'},
    'integration_job': {'state': 'SUCCEEDED'},
    'sheet_sync_record': {'state': 'SYNCED'},
    'content_item': {'slug': 'demo-home', 'locale': 'en'},
    'content_revision': {'title': 'Welcome to Demo Nigson', 'state': 'DRAFT',
                         'body': {'blocks': [{'type': 'paragraph', 'text': 'Synthetic content for testing.'}]}},
    'lead': {'name': 'Demo Customer', 'email': 'demo.lead@example.invalid',
             'subject': 'Demo solar installation enquiry', 'message': 'Synthetic enquiry for a solar installation.', 'source': 'DEMO'},
    'lead_activity': {'activity_type': 'NOTE', 'note': 'Demo lead created for testing.'},
    'communication_destination': {'status': 'SUPPRESSED', 'consented_purposes': []},
    'consent_event': {'granted': False},
    'destination_verification': {'status': 'EXPIRED'},
    'inbound_message': {'provider': 'DEMO', 'channel': 'WHATSAPP', 'disposition': 'IGNORED'},
    'newsletter_subscription': {'state': 'SUPPRESSED'},
    'abandoned_cart_candidate': {'status': 'SUPPRESSED'},
    'ingestion_job': {'status': 'SUCCEEDED'},
    'ai_run': {'status': 'CANCELLED'},
    'conversation': {'state': 'CLOSED', 'anonymous_session_hash': 'd' * 64},
    'embedding_revision': {'dimensions': 1024, 'normalization': True, 'enabled': False},
    'knowledge_chunk': {'token_count': 1},
    'tool_invocation': {'outcome': 'DENIED'},
    'spring_session': {'max_inactive_interval': 60, 'creation_time': 0, 'last_access_time': 0, 'expiry_time': 1},
    'spring_session_attributes': {'attribute_name': 'demo.expired', 'attribute_bytes': '\\x'},
}


def value(table, field):
    name, datatype = field['name'], field['type']
    owner, short = table.split('.')
    overrides = OVERRIDES.get(short, {})
    if name in overrides:
        return overrides[name]
    if field['foreign'] and not field['nullable']:
        target, _, column = field['foreign'].partition('.')
        return value(owner + '.' + target, next(f for f in TABLES[owner + '.' + target]['fields'] if f['name'] == (column or 'id')))
    if field['nullable']:
        # Populate ownership for otherwise anonymous file assets; no FK required.
        if short == 'file_asset' and name == 'owner_party_id':
            return uid('commerce.party')
        return None
    if name == 'id':
        return uid(table)
    if name in ('primary_id', 'session_id'):
        return uid('commerce.spring_session')
    if name in ('created_by', 'updated_by', 'actor_subject'):
        return 'demo-seed-v1'
    if name == 'country_code':
        return 'NG'
    if name in ('currency_code', 'preferred_currency'):
        return 'NGN'
    if name == 'locale':
        return 'en'
    checks = TABLES[table]['checks']
    choices = re.search(r'\b' + re.escape(name) + r' IN \(([^)]+)\)', checks)
    if choices:
        first = choices[1].split(',')[0].strip()
        return int(first) if re.fullmatch(r'-?\d+', first) else first
    equality = re.search(r'\b' + re.escape(name) + r'=([A-Z][A-Z_]+)\b', checks)
    if equality:
        return equality[1]
    if datatype == 'jsonb':
        return [] if re.search(r'jsonb_typeof\(' + name + r'\)=\x27?array', checks) or field['default'] == '[]' else {}
    if datatype == 'boolean':
        return False
    if datatype == 'uuid':
        return uid(table + '/' + name)
    if datatype == 'bytea':
        # Opaque rejected fixtures, deliberately not usable application secrets.
        return '\\x' + '00' * 32
    if datatype.startswith('vector('):
        return '[' + ','.join(['1'] + ['0'] * 1023) + ']'
    if datatype in ('timestamptz', 'date'):
        return '2099-01-01T00:00:00Z' if any(s in name for s in ('expires', 'ends', 'valid_until', 'retention', 'next_attempt')) else '2026-01-01T00:00:00Z'
    if datatype.startswith(('integer', 'bigint', 'smallint', 'numeric')):
        return int(field['default']) if field['default'] is not None and re.fullmatch(r'\d+', str(field['default'])) else 1
    if datatype == 'char(64)':
        return hashlib.sha256((table + '/' + name).encode()).hexdigest()
    if datatype == 'char(3)':
        return 'NGN'
    result = 'demo-' + short.replace('_', '-') + '-' + name.replace('_', '-')
    length = re.search(r'\((\d+)\)', datatype)
    return result[:int(length[1])] if length else result


def rows_for(names):
    """Topologically order required FK dependencies; nullable cycles stay NULL."""
    pending, ordered = set(names), []
    while pending:
        ready = [t for t in sorted(pending) if all(
            t.split('.')[0] + '.' + f['foreign'].split('.')[0] in ordered
            for f in TABLES[t]['fields'] if f['foreign'] and not f['nullable'])]
        if not ready:
            raise ValueError('Unresolved required FK dependencies: ' + ', '.join(sorted(pending)))
        ordered.extend(ready)
        pending.difference_update(ready)
    return [(t, {f['name']: value(t, f) for f in TABLES[t]['fields']}) for t in ordered]


def primary_fields(table):
    if table == 'commerce.spring_session_attributes':
        return ['session_primary_id', 'attribute_name']
    return [f['name'] for f in TABLES[table]['fields'] if f['primary']]


def render(names):
    rows = rows_for(names)
    statements = ['BEGIN;', "SET LOCAL lock_timeout = '5s';", "SET LOCAL statement_timeout = '30s';"]
    for table, row in rows:
        # Use the installed currency baseline rather than creating another NGN.
        if table == 'commerce.currency':
            statements.append("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM commerce.currency WHERE code='NGN' AND minor_unit=2) THEN RAISE EXCEPTION 'NGN baseline missing or incompatible'; END IF; END $$;")
            continue
        columns = ','.join(row)
        expressions = []
        for field, val in row.items():
            expressions.append(literal(val))
        # Conflict only on fixture primary keys; unrelated unique collisions fail.
        pk = ','.join(primary_fields(table))
        statements.append(f'INSERT INTO {table} ({columns}) VALUES ({",".join(expressions)}) ON CONFLICT ({pk}) DO NOTHING;')
    statements.append('COMMIT;')
    # Verify every fixture by its primary key, never merely by table nonemptiness.
    for table, row in rows:
        condition = "code='NGN'" if table == 'commerce.currency' else ' AND '.join(
            f"{name}={literal(row[name])}" for name in primary_fields(table))
        statements.append(f"SELECT '{table}|'||count(*) FROM {table} WHERE {condition};")
    return '\n'.join(statements) + '\n'


def docker(*args, input=None):
    result = subprocess.run(['docker', *args], cwd=ROOT, input=input, text=True, capture_output=True, timeout=120)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='Apply to local Compose PostgreSQL; default prints SQL for review')
    parser.add_argument('--service', choices=('postgres', 'vector-db'), default='postgres')
    args = parser.parse_args()
    if os.environ.get('APP_ENV', 'local') not in ('local', 'test') or os.environ.get('ENV', 'local') not in ('local', 'test'):
        parser.error('Demo seed requires local/test environment')
    context = json.loads(docker('context', 'inspect'))[0]
    if not context['Endpoints']['docker']['Host'].startswith('unix://') or os.environ.get('DOCKER_HOST', 'unix://').startswith(('tcp:', 'ssh:')):
        parser.error('Demo seed requires a local Unix Docker endpoint')
    database = 'nigson' if args.service == 'postgres' else 'nigson_vector_test'
    base = ('compose', 'exec', '-T', args.service, 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', database, '-v', 'ON_ERROR_STOP=1')
    names = docker(*base, '-c', "SELECT schemaname||'.'||tablename FROM pg_tables WHERE schemaname IN ('commerce','experience','integration','ai') AND tablename NOT LIKE 'databasechangelog%' ORDER BY 1").splitlines()
    unknown = set(names) - TABLES.keys()
    if unknown:
        raise ValueError('Uncatalogued application tables: ' + ', '.join(sorted(unknown)))
    if not names:
        parser.error('No installed application tables; run local migrations first')
    sql = render(names)
    if not args.apply:
        print(sql, end='')
        return
    output = docker(*base, input=sql)
    verified = [line for line in output.splitlines() if line.endswith('|1')]
    if len(verified) != len(names):
        raise RuntimeError('Fixture coverage failed: ' + output)
    print(output)
    print(f'PASS: {len(verified)} installed application tables contain demo fixtures.')
    absent = sorted(set(TABLES) - set(names))
    print(f'{len(absent)} catalog tables absent from this database (later releases / separate AI database); no migrations performed.')


if __name__ == '__main__':
    main()
