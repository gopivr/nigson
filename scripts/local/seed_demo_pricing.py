#!/usr/bin/env python3
"""User-approved synthetic NG prices/policies, restricted to local demo electronics."""
import argparse
import json
import os
from seed_demo import docker, uid, literal
from seed_electronics import CATEGORIES

BOOK = uid('electronics/retail-price-book')
TAX = {'schemaVersion': 1, 'mode': 'NONE', 'rate': '0', 'rounding': 'HALF_UP', 'approvalReference': 'SYNTHETIC_LOCAL_TEST_ONLY'}
RETURNS = 'SYNTHETIC-LOCAL-RETURNS-V1'
# NGN major units, deliberately synthetic, never a real commercial price list.
BASES = (450000, 180000, 95000, 25000, 35000, 30000, 20000, 15000)


def render(with_cart_destination=False):
    sql = ['BEGIN;', "SET LOCAL lock_timeout='5s';", "SET LOCAL statement_timeout='15s';"]
    sql.append(f"""DO $$ BEGIN
IF NOT EXISTS(SELECT 1 FROM commerce.market WHERE code='NG' AND enabled
 AND (retail_price_book_id IS NULL OR retail_price_book_id={literal(BOOK)}::uuid)
 AND (tax_policy='{{}}'::jsonb OR tax_policy={literal(TAX)}::jsonb)
 AND (returns_policy_revision IS NULL OR returns_policy_revision={literal(RETURNS)}))
THEN RAISE EXCEPTION 'REFUSING_TO_REPLACE_NONDEMO_MARKET_POLICY'; END IF;
END $$;""")
    sql.append(f"""INSERT INTO commerce.price_book(id,created_by,updated_by,code,currency_code,segment,revision,effective_from,active)
VALUES ({literal(BOOK)},'local-demo-pricing','local-demo-pricing','SYNTHETIC-LOCAL-ELECTRONICS-NGN','NGN','RETAIL',1,'2026-01-01T00:00:00Z',true)
ON CONFLICT(id) DO NOTHING;""")
    for (category, products), base in zip(CATEGORIES.items(), BASES, strict=True):
        slug = category.lower().replace(' ', '-')
        for number, _ in enumerate(products, 1):
            sku = uid('electronics/sku/' + slug + '/' + str(number))
            entry = uid('electronics/price/' + slug + '/' + str(number))
            sql.append(f"""INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT {literal(entry)},'local-demo-pricing','local-demo-pricing',{literal(BOOK)},id,1,{base + (number-1)*1000}
FROM commerce.sku WHERE id={literal(sku)} AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;""")
    sql.append(f"""UPDATE commerce.market SET retail_price_book_id={literal(BOOK)},tax_policy={literal(TAX)}::jsonb,
returns_policy_revision={literal(RETURNS)},policy_revision=policy_revision+1,version=version+1,
updated_at=now(),updated_by='local-demo-pricing'
WHERE code='NG' AND (retail_price_book_id IS DISTINCT FROM {literal(BOOK)}::uuid
OR tax_policy IS DISTINCT FROM {literal(TAX)}::jsonb OR returns_policy_revision IS DISTINCT FROM {literal(RETURNS)});""")
    sql.append(f"""DO $$ BEGIN
IF (SELECT count(*) FROM commerce.price_entry WHERE price_book_id={literal(BOOK)})<>24
THEN RAISE EXCEPTION 'DEMO_ELECTRONICS_PRICE_COVERAGE_REQUIRED'; END IF;
END $$;""")
    if with_cart_destination:
        rules = literal({'schemaVersion': 1, 'requiredFields': ['RECIPIENT', 'LINE1', 'CITY'], 'postalPattern': None, 'regionCodes': []})
        sql.append(f"""DO $$ BEGIN
IF EXISTS(SELECT 1 FROM commerce.market_destination d JOIN commerce.market m ON m.id=d.market_id
 WHERE m.code='NG' AND d.country_code='NG' AND NOT
 ((d.created_by='approved-launch:ADR-008' AND NOT d.delivery_enabled AND d.address_rules='{{}}'::jsonb)
 OR d.updated_by='local-demo-pricing'))
THEN RAISE EXCEPTION 'REFUSING_TO_REPLACE_NONDEMO_DESTINATION'; END IF;
END $$;
INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled)
SELECT {literal(uid('electronics/test-ng-destination'))},'local-demo-pricing','local-demo-pricing',id,'NG',{rules}::jsonb,true
FROM commerce.market m WHERE code='NG' AND NOT EXISTS(SELECT 1 FROM commerce.market_destination d WHERE d.market_id=m.id AND d.country_code='NG');
WITH changed AS (
 UPDATE commerce.market_destination d SET address_rules={rules}::jsonb,delivery_enabled=true,
 updated_by='local-demo-pricing',updated_at=now(),version=d.version+1 FROM commerce.market m
 WHERE d.market_id=m.id AND m.code='NG' AND d.country_code='NG'
 AND (NOT d.delivery_enabled OR d.address_rules IS DISTINCT FROM {rules}::jsonb) RETURNING d.market_id
) UPDATE commerce.market SET version=version+1,policy_revision=policy_revision+1,
 updated_by='local-demo-pricing',updated_at=now() WHERE id IN(SELECT market_id FROM changed);""")
    sql.append('COMMIT;')
    return '\n'.join(sql)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-cart-destination', action='store_true', help='Enable the approved synthetic local NG destination for cart testing')
    args = parser.parse_args()
    if os.environ.get('ENV', 'local') != 'local' or os.environ.get('APP_ENV', 'local') != 'local':
        raise ValueError('LOCAL_ONLY')
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):
        raise ValueError('LOCAL_DOCKER_REQUIRED')
    context = json.loads(docker('context', 'inspect'))[0]
    if not context['Endpoints']['docker']['Host'].startswith('unix://'):
        raise ValueError('LOCAL_DOCKER_REQUIRED')
    config = json.loads(docker('compose', 'config', '--format', 'json'))
    if config.get('name') != 'nigson-local' or config['services']['postgres']['environment'].get('APP_ENV') != 'local':
        raise ValueError('LOCAL_PROJECT_REQUIRED')
    docker('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', 'nigson', '-v', 'ON_ERROR_STOP=1', input=render(args.with_cart_destination))
    print('PASS: 24 synthetic local NGN retail prices and test policies; repeat-safe.')
    if args.with_cart_destination:
        print('PASS: synthetic local NG cart destination enabled; no provider qualification implied.')


if __name__ == '__main__':
    main()
