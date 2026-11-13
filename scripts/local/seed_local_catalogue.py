#!/usr/bin/env python3
"""Seed a synthetic, local-only storefront catalogue and activate NG selling.

This is a developer convenience for the `nigson-local` Compose project only. It
writes clearly labelled synthetic policy/price rows so the storefront and staff
APIs can be exercised end to end without inventing production approvals.

Guards:
  * refuses anything except the local Docker context and the `nigson-local` project
  * refuses unless the resolved commerce `APP_ENV` is `local`
  * never runs as part of a migration; call it explicitly
  * no provider is mocked or called
"""

import argparse
import json
import subprocess
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "https://nigson.invalid/local-synthetic-catalogue/v1")
ACTOR = "local-synthetic-seed"
CONTAINER = "nigson-local-postgres-1"
DATABASE_USER = "nigson_bootstrap"
DATABASE_NAME = "nigson"

TAX_POLICY = {
    "schemaVersion": 1,
    "mode": "INCLUSIVE",
    "rate": "0.075",
    "rounding": "HALF_UP",
    "approvalReference": "LOCAL-SYNTHETIC-VAT-NOT-A-REAL-APPROVAL",
}
RETURNS_REVISION = "LOCAL-SYNTHETIC-RETURNS-1"
ADDRESS_RULES = {
    "schemaVersion": 1,
    "requiredFields": ["RECIPIENT", "PHONE", "LINE1", "CITY", "REGION"],
    "postalPattern": "^[0-9]{6}$",
    "regionCodes": [],
}
PRICE_BOOK_CODE = "LOCAL-RETAIL-NGN"

CATEGORIES = [
    ("Power Banks", "power-banks", 10),
    ("Earbuds", "earbuds", 20),
    ("Cables", "cables", 30),
    ("Chargers", "chargers", 40),
    ("Extension Sockets", "extension-sockets", 50),
    ("Wireless Chargers", "wireless-chargers", 60),
]
BRANDS = [("Nexa", "nexa"), ("Volt", "volt")]

# name, slug, category slug, brand slug, description, sku code, unit, pack size, weight kg, price NGN
PRODUCTS = [
    ("Nexa Power Bank 20000 mAh", "nexa-power-bank-20000", "power-banks", "nexa",
     "20,000 mAh fast-charge power bank with dual USB-C output.", "NX-PB20-BLK", "PIECE", "1", "0.420", "24999.00"),
    ("Nexa Air Wireless Earbuds", "nexa-air-wireless-earbuds", "earbuds", "nexa",
     "True wireless earbuds with active noise cancellation and charging case.", "NX-EB-AIR-WHT", "PIECE", "1", "0.060", "18999.00"),
    ("Nexa USB-C Braided Cable 2 m", "nexa-usb-c-braided-cable-2m", "cables", "nexa",
     "2 m braided USB-C to USB-C cable rated for 100 W charging.", "NX-CB-USBC-2M", "PIECE", "1", "0.080", "4999.00"),
    ("Nexa 65 W GaN Charger", "nexa-65w-gan-charger", "chargers", "nexa",
     "Compact 65 W GaN wall charger with two USB-C and one USB-A port.", "NX-CH-65W-BLK", "PIECE", "1", "0.150", "15999.00"),
    ("Volt 6-Way Extension Socket", "volt-6-way-extension-socket", "extension-sockets", "volt",
     "Surge-protected six-outlet extension socket with 3 m cable.", "VT-EX6-WHT", "PIECE", "1", "0.900", "12999.00"),
    ("Volt 15 W Wireless Charger", "volt-15w-wireless-charger", "wireless-chargers", "volt",
     "15 W Qi wireless charging pad with USB-C input.", "VT-WC15-BLK", "PIECE", "1", "0.120", "13999.00"),
    ("Volt Slim Power Bank 10000 mAh", "volt-slim-power-bank-10000", "power-banks", "volt",
     "10,000 mAh slim power bank with 20 W USB-C power delivery.", "VT-PB10-SLV", "PIECE", "1", "0.260", "14999.00"),
    ("Nexa Studio Earbuds", "nexa-studio-earbuds", "earbuds", "nexa",
     "Studio-tuned wireless earbuds with spatial audio support.", "NX-EB-STU-BLK", "PIECE", "1", "0.065", "27999.00"),
]

STOCK_LOCATION = ("SYN-LAGOS", "Synthetic Lagos Warehouse")
ON_HAND = "250"


def identify(kind: str, key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{key}"))


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def build_sql() -> str:
    statements = ["SET client_min_messages TO WARNING;"]
    statements.append(
        f"INSERT INTO commerce.stock_location(id,created_by,updated_by,code,name,active) "
        f"VALUES ({sql_literal(identify('location', STOCK_LOCATION[0]))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},"
        f"{sql_literal(STOCK_LOCATION[0])},{sql_literal(STOCK_LOCATION[1])},true) "
        f"ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,active=true,updated_by=EXCLUDED.updated_by,updated_at=now();"
    )
    for name, slug in BRANDS:
        statements.append(
            f"INSERT INTO commerce.brand(id,created_by,updated_by,name,slug,active) "
            f"VALUES ({sql_literal(identify('brand', slug))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},"
            f"{sql_literal(name)},{sql_literal(slug)},true) "
            f"ON CONFLICT (slug) DO UPDATE SET name=EXCLUDED.name,active=true,deleted_at=NULL,updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
    for name, slug, order in CATEGORIES:
        statements.append(
            f"INSERT INTO commerce.category(id,created_by,updated_by,name,slug,sort_order,active) "
            f"VALUES ({sql_literal(identify('category', slug))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},"
            f"{sql_literal(name)},{sql_literal(slug)},{order},true) "
            f"ON CONFLICT (slug) DO UPDATE SET name=EXCLUDED.name,sort_order=EXCLUDED.sort_order,active=true,deleted_at=NULL,updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
    for name, slug, category, brand, description, sku, unit, pack, weight, price in PRODUCTS:
        product_id = identify("product", slug)
        sku_id = identify("sku", sku)
        statements.append(
            f"INSERT INTO commerce.product(id,created_by,updated_by,brand_id,name,slug,description,specifications,status,revision,published_at) "
            f"VALUES ({sql_literal(product_id)},{sql_literal(ACTOR)},{sql_literal(ACTOR)},{sql_literal(identify('brand', brand))},"
            f"{sql_literal(name)},{sql_literal(slug)},{sql_literal(description)},'{{}}'::jsonb,'ACTIVE',1,now()) "
            f"ON CONFLICT (slug) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,status='ACTIVE',"
            f"brand_id=EXCLUDED.brand_id,published_at=COALESCE(commerce.product.published_at,now()),updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
        statements.append(
            f"INSERT INTO commerce.product_category(id,created_by,updated_by,product_id,category_id) "
            f"VALUES ({sql_literal(identify('product-category', slug + ':' + category))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},"
            f"{sql_literal(product_id)},{sql_literal(identify('category', category))}) "
            f"ON CONFLICT (product_id,category_id) DO NOTHING;"
        )
        statements.append(
            f"INSERT INTO commerce.sku(id,created_by,updated_by,product_id,code,variant_name,unit,quantity_scale,pack_size,weight_kg,active) "
            f"VALUES ({sql_literal(sku_id)},{sql_literal(ACTOR)},{sql_literal(ACTOR)},{sql_literal(product_id)},"
            f"{sql_literal(sku)},{sql_literal(name)},{sql_literal(unit)},0,{pack},{weight},true) "
            f"ON CONFLICT (code) DO UPDATE SET product_id=EXCLUDED.product_id,variant_name=EXCLUDED.variant_name,"
            f"unit=EXCLUDED.unit,pack_size=EXCLUDED.pack_size,weight_kg=EXCLUDED.weight_kg,active=true,deleted_at=NULL,"
            f"updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
        statements.append(
            f"INSERT INTO commerce.product_market_eligibility(id,created_by,updated_by,product_id,market_id,eligible,reason) "
            f"SELECT {sql_literal(identify('eligibility', slug))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},p.id,m.id,true,'' "
            f"FROM commerce.product p, commerce.market m WHERE p.id={sql_literal(product_id)} AND m.code='NG' "
            f"ON CONFLICT (product_id,market_id) DO UPDATE SET eligible=true,reason='',updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
        statements.append(
            f"INSERT INTO commerce.inventory_balance(id,created_by,updated_by,sku_id,location_id,on_hand,reserved,allocated) "
            f"VALUES ({sql_literal(identify('balance', sku))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},{sql_literal(sku_id)},"
            f"{sql_literal(identify('location', STOCK_LOCATION[0]))},{ON_HAND},0,0) "
            f"ON CONFLICT (sku_id,location_id) DO UPDATE SET on_hand=EXCLUDED.on_hand,updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
    book_id = identify("price-book", PRICE_BOOK_CODE)
    statements.append(
        f"INSERT INTO commerce.price_book(id,created_by,updated_by,code,currency_code,segment,revision,effective_from,effective_until,active) "
        f"VALUES ({sql_literal(book_id)},{sql_literal(ACTOR)},{sql_literal(ACTOR)},{sql_literal(PRICE_BOOK_CODE)},'NGN','RETAIL',1,now()-interval '1 day',NULL,true) "
        f"ON CONFLICT DO NOTHING;"
    )
    statements.append(
        f"UPDATE commerce.price_book SET active=true,effective_from=LEAST(effective_from,now()-interval '1 day'),"
        f"updated_by={sql_literal(ACTOR)},updated_at=now() WHERE code={sql_literal(PRICE_BOOK_CODE)};"
    )
    for _name, _slug, _category, _brand, _description, sku, _unit, _pack, _weight, price in PRODUCTS:
        statements.append(
            f"INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount) "
            f"VALUES ({sql_literal(identify('price-entry', sku + ':1'))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},{sql_literal(book_id)},"
            f"{sql_literal(identify('sku', sku))},1,{price}) "
            f"ON CONFLICT (price_book_id,sku_id,minimum_quantity) DO UPDATE SET unit_amount=EXCLUDED.unit_amount,updated_by=EXCLUDED.updated_by,updated_at=now();"
        )
    tax = json.dumps(TAX_POLICY, separators=(",", ":"))
    rules = json.dumps(ADDRESS_RULES, separators=(",", ":"))
    statements.append(
        f"UPDATE commerce.market SET enabled=true,tax_policy={sql_literal(tax)}::jsonb,"
        f"returns_policy_revision={sql_literal(RETURNS_REVISION)},retail_price_book_id={sql_literal(book_id)},"
        f"policy_revision=policy_revision+1,version=version+1,updated_by={sql_literal(ACTOR)},updated_at=now() WHERE code='NG';"
    )
    statements.append(
        f"INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled) "
        f"SELECT {sql_literal(identify('destination', 'NG'))},{sql_literal(ACTOR)},{sql_literal(ACTOR)},m.id,'NG',"
        f"{sql_literal(rules)}::jsonb,true FROM commerce.market m WHERE m.code='NG' "
        f"ON CONFLICT (market_id,country_code) DO UPDATE SET address_rules=EXCLUDED.address_rules,delivery_enabled=true,"
        f"updated_by=EXCLUDED.updated_by,updated_at=now();"
    )
    return "\n".join(statements) + "\n"


def docker(*arguments: str) -> str:
    result = subprocess.run(["docker", *arguments], cwd=ROOT, text=True, capture_output=True, timeout=180)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "docker command failed")
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed the local synthetic storefront catalogue.")
    parser.add_argument("--dry-run", action="store_true", help="print the SQL instead of applying it")
    arguments = parser.parse_args()
    try:
        if not json.loads(docker("context", "inspect"))[0]["Endpoints"]["docker"]["Host"].startswith(("unix://", "npipe://")):
            raise ValueError("Local Docker context required")
        config = json.loads(docker("compose", "--profile", "app", "config", "--format", "json"))
        if config.get("name") != "nigson-local":
            raise ValueError("Only the nigson-local Compose project may be seeded")
        environment = config["services"]["commerce"]["environment"]
        if environment.get("APP_ENV") != "local":
            raise ValueError("Local seeding requires commerce APP_ENV=local")
        sql = build_sql()
        if arguments.dry_run:
            print(sql)
            return 0
        result = subprocess.run(
            ["docker", "exec", "-i", CONTAINER, "psql", "-U", DATABASE_USER, "-d", DATABASE_NAME, "-v", "ON_ERROR_STOP=1", "-f", "-"],
            cwd=ROOT, input=sql, text=True, capture_output=True, timeout=120,
        )
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or "psql seed failed")
        print("SYNTHETIC LOCAL SEED APPLIED (not approved production data)")
        print(docker("exec", CONTAINER, "psql", "-U", DATABASE_USER, "-d", DATABASE_NAME, "-tAc",
                     "SELECT 'market='||enabled||' categories='||(SELECT count(*) FROM commerce.category WHERE active)||"
                     "' products='||(SELECT count(*) FROM commerce.product WHERE status='ACTIVE')||"
                     "' skus='||(SELECT count(*) FROM commerce.sku WHERE active)||"
                     "' prices='||(SELECT count(*) FROM commerce.price_entry e JOIN commerce.price_book b ON b.id=e.price_book_id WHERE b.active AND b.segment='RETAIL')"
                     " FROM commerce.market WHERE code='NG'"))
        return 0
    except Exception as failure:  # noqa: BLE001 - explicit CLI failure
        print(f"SEED FAILED: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
