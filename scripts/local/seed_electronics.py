#!/usr/bin/env python3
"""Add a repeatable local electronics catalogue with actual inventory balances."""
import argparse
import os
import json
from seed_demo import docker, uid, literal

# name, stock, specifications; fixture values are synthetic, not real listings.
CATEGORIES = {
 'Laptops': [
  ('14-inch Ultrabook', 18, {'processor': 'Demo Core i5', 'memory': '16 GB', 'storage': '512 GB SSD'}),
  ('15-inch Performance Laptop', 9, {'processor': 'Demo Core i7', 'memory': '32 GB', 'storage': '1 TB SSD'}),
  ('13-inch Student Laptop', 24, {'processor': 'Demo Core i3', 'memory': '8 GB', 'storage': '256 GB SSD'})],
 'Smartphones': [
  ('5G Smartphone 128 GB', 35, {'display': '6.5 inch', 'storage': '128 GB', 'battery': '5000 mAh'}),
  ('Pro Smartphone 256 GB', 12, {'display': '6.7 inch', 'storage': '256 GB', 'camera': '50 MP'}),
  ('Compact Smartphone 64 GB', 0, {'display': '6.1 inch', 'storage': '64 GB', 'battery': '4000 mAh'})],
 'Tablets': [
  ('10-inch Android Tablet', 16, {'display': '10.1 inch', 'storage': '128 GB', 'connectivity': 'Wi-Fi'}),
  ('11-inch Drawing Tablet', 6, {'display': '11 inch', 'storage': '256 GB', 'accessory': 'Stylus'}),
  ('8-inch Kids Tablet', 22, {'display': '8 inch', 'storage': '64 GB', 'accessory': 'Protective case'})],
 'Audio': [
  ('Wireless Noise-Cancelling Headphones', 28, {'connection': 'Bluetooth', 'battery_life': '30 hours'}),
  ('True Wireless Earbuds', 45, {'connection': 'Bluetooth', 'battery_life': '6 hours'}),
  ('Portable Bluetooth Speaker', 8, {'output': '20 W', 'connection': 'Bluetooth'})],
 'Gaming': [
  ('Mechanical Gaming Keyboard', 20, {'connection': 'USB-C', 'switches': 'Mechanical'}),
  ('Wireless Gaming Mouse', 30, {'connection': '2.4 GHz', 'sensitivity': '16000 DPI'}),
  ('27-inch Gaming Monitor', 7, {'resolution': '2560 x 1440', 'refresh_rate': '144 Hz'})],
 'Networking': [
  ('Wi-Fi 6 Dual-Band Router', 15, {'wireless_standard': 'Wi-Fi 6', 'ports': '4 LAN'}),
  ('8-Port Gigabit Switch', 19, {'ports': '8 Ethernet', 'speed': '1 Gbps'}),
  ('USB Wi-Fi Adapter', 0, {'connection': 'USB 3.0', 'wireless_standard': 'Wi-Fi 5'})],
 'Storage': [
  ('1 TB Portable SSD', 32, {'capacity': '1 TB', 'connection': 'USB-C'}),
  ('2 TB External Hard Drive', 14, {'capacity': '2 TB', 'connection': 'USB 3.0'}),
  ('128 GB USB Flash Drive', 60, {'capacity': '128 GB', 'connection': 'USB-A'})],
 'Power and Accessories': [
  ('65 W USB-C GaN Charger', 38, {'output': '65 W', 'ports': '2 USB-C'}),
  ('20000 mAh Power Bank', 26, {'capacity': '20000 mAh', 'output': '22.5 W'}),
  ('6-Outlet Surge Protector', 5, {'outlets': '6', 'cable_length': '2 m'})],
}


def render(publish=False, with_packaging=False):
    statements = ['BEGIN;', "SET LOCAL lock_timeout='5s';", "SET LOCAL statement_timeout='30s';"]
    def insert(table, values):
        statements.append('INSERT INTO commerce.' + table + '(' + ','.join(values) + ') VALUES (' + ','.join(literal(v) for v in values.values()) + ') ON CONFLICT (id) DO NOTHING;')
    def row(key, **values):
        return {'id': uid('electronics/' + key), 'created_by': 'demo-electronics-v1', 'updated_by': 'demo-electronics-v1', **values}
    location = uid('electronics/location')
    insert('stock_location', row('location', code='DEMO-ELECTRONICS-LAGOS', name='Demo Electronics Lagos Warehouse', active=True))
    brand = uid('electronics/brand')
    insert('brand', row('brand', name='Demo Electronics', slug='demo-electronics', active=True))
    for index, (category, products) in enumerate(CATEGORIES.items(), 1):
        slug = category.lower().replace(' ', '-')
        category_id = uid('electronics/category/' + slug)
        insert('category', row('category/' + slug, name=category, slug='demo-' + slug, sort_order=index, active=True))
        for number, (name, stock, specs) in enumerate(products, 1):
            key = slug + '/' + str(number)
            product = uid('electronics/product/' + key)
            sku = uid('electronics/sku/' + key)
            code = f'DEMO-EL-{index:02d}-{number:02d}'
            product_slug = 'demo-' + name.lower().replace(' ', '-').replace('.', '')
            insert('product', row('product/' + key, name='Demo ' + name, slug=product_slug,
                description=name + '. Synthetic electronics product for local catalogue and inventory validation.',
                brand_id=brand, division_code='SYNTHETIC_ELECTRONICS', specifications=specs, status='DRAFT', revision=1))
            insert('product_category', row('product-category/' + key, product_id=product, category_id=category_id))
            insert('sku', row('sku/' + key, product_id=product, code=code, variant_name='Standard', unit='unit', active=True))
            insert('inventory_balance', row('balance/' + key, sku_id=sku, location_id=location, on_hand=stock, reserved=0, allocated=0))
            # Opening stock and balance commit together; no provider work is queued.
            insert('inventory_movement', {'id': uid('electronics/movement/' + key), 'created_by': 'demo-electronics-v1',
                'balance_id': uid('electronics/balance/' + key), 'operation_key': 'demo-electronics-opening/' + key,
                'movement_type': 'ADJUST', 'delta_on_hand': stock, 'delta_reserved': 0, 'delta_allocated': 0,
                'reason': 'Synthetic electronics opening stock for local testing'})
    if publish:
        statements.append("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM commerce.market WHERE code='NG' AND currency_code='NGN') THEN RAISE EXCEPTION 'NG/NGN market baseline required'; END IF; END $$;")
        statements.append("UPDATE commerce.market SET enabled=true,updated_at=now(),updated_by='demo-electronics-publication',version=version+1 WHERE code='NG' AND NOT enabled;")
        for products_index, (category, products) in enumerate(CATEGORIES.items(), 1):
            slug = category.lower().replace(' ', '-')
            for number, _ in enumerate(products, 1):
                key = slug + '/' + str(number)
                product = uid('electronics/product/' + key)
                eligibility = uid('electronics/ng-eligibility/' + key)
                statements.append(f"INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible,reason) SELECT {literal(eligibility)},'demo-electronics-v1','demo-electronics-publication',{literal(product)},id,true,'Local synthetic electronics catalogue visibility' FROM commerce.market WHERE code='NG' ON CONFLICT(product_id,market_id) DO UPDATE SET eligible=true,updated_by='demo-electronics-publication',updated_at=now(),version=commerce.product_market_eligibility.version+1 WHERE commerce.product_market_eligibility.created_by='demo-electronics-v1' AND NOT commerce.product_market_eligibility.eligible;")
        statements.append("UPDATE commerce.product SET status='ACTIVE',published_at=coalesce(published_at,now()),updated_at=now(),updated_by='demo-electronics-publication',version=version+1,revision=revision+1 WHERE created_by='demo-electronics-v1' AND status='DRAFT' AND deleted_at IS NULL;")
        statements.append("DO $$ BEGIN IF (SELECT count(*) FROM commerce.product p JOIN commerce.product_market_eligibility e ON e.product_id=p.id JOIN commerce.market m ON m.id=e.market_id WHERE p.created_by='demo-electronics-v1' AND p.status='ACTIVE' AND p.deleted_at IS NULL AND e.eligible AND m.enabled AND m.code='NG') <> 24 THEN RAISE EXCEPTION 'Expected all 24 demo products to be publicly eligible'; END IF; END $$;")
        statements.append(f"INSERT INTO commerce.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason,new_value) VALUES ({literal(uid('electronics/publication-audit'))},'demo-electronics-publication','local-demo-provisioner','SYSTEM','DEMO_CATALOGUE_PUBLICATION','catalogue',{literal(brand)},now(),{literal(uid('electronics/publication-correlation'))},'User requested demo products visible to other local users', '{{\"market\":\"NG\",\"products\":24}}'::jsonb) ON CONFLICT(id) DO NOTHING;")
    if with_packaging:
        # User-approved synthetic sandbox fixture measurements; never real merchandise.
        packaging = {'schemaVersion': 1, 'maximumUnitsPerPackage': '1', 'unitWeightKg': '0.5', 'tareWeightKg': '0.1', 'lengthCm': '20', 'widthCm': '15', 'heightCm': '10'}
        statements.append(f"UPDATE commerce.sku SET carrier_packaging={literal(packaging)}::jsonb, version=version+1, updated_at=now(), updated_by='synthetic-local-packaging' WHERE created_by='demo-electronics-v1' AND code LIKE 'DEMO-EL-%' AND carrier_packaging IS NULL AND deleted_at IS NULL;")
        statements.append(f"INSERT INTO commerce.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason,new_value) VALUES ({literal(uid('electronics/packaging-audit'))},'synthetic-local-packaging','local-demo-provisioner','SYSTEM','DEMO_PACKAGING_SEEDED','catalogue',{literal(brand)},now(),{literal(uid('electronics/packaging-correlation'))},'User approved synthetic local demo measurements for sandbox quotes; not measured product data',{literal(packaging)}::jsonb) ON CONFLICT(id) DO NOTHING;")
    statements.append('COMMIT;')
    statements.append("SELECT 'products|'||count(*) FROM commerce.product WHERE created_by='demo-electronics-v1';")
    statements.append("SELECT 'categories|'||count(*) FROM commerce.category WHERE created_by='demo-electronics-v1';")
    statements.append("SELECT 'balances|'||count(*) FROM commerce.inventory_balance WHERE created_by='demo-electronics-v1';")
    return '\n'.join(statements) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish', action='store_true', help='Publish synthetic electronics for all local users in NG; no price/tax/delivery policy changes')
    parser.add_argument('--with-packaging', action='store_true', help='Add user-approved synthetic measurements only to local demo SKUs missing packaging')
    args = parser.parse_args()
    if os.environ.get('ENV', 'local') not in ('local', 'test') or os.environ.get('APP_ENV', 'local') not in ('local', 'test'):
        raise ValueError('Local/test environment required')
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):
        raise ValueError('Local Unix Docker endpoint required')
    context = json.loads(docker('context', 'inspect'))[0]
    if not context['Endpoints']['docker']['Host'].startswith('unix://'):
        raise ValueError('Local Unix Docker endpoint required')
    config = json.loads(docker('compose', 'config', '--format', 'json'))
    if config.get('name') != 'nigson-local' or config['services']['postgres']['environment'].get('APP_ENV') != 'local':
        raise ValueError('Expected local nigson-local Compose database')
    output = docker('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', 'nigson', '-v', 'ON_ERROR_STOP=1', input=render(publish=args.publish, with_packaging=args.with_packaging))
    if set(output.splitlines()) != {'products|24', 'categories|8', 'balances|24'}:
        raise RuntimeError('Electronics fixture coverage failed: ' + output)
    print(output)
    print('PASS: electronics products, categories, SKUs, stock balances and opening movements populated.')


if __name__ == '__main__':
    main()
