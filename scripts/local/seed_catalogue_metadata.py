#!/usr/bin/env python3
"""Seed catalogue.metadata feature configuration into local PostgreSQL."""
import json
import subprocess
import uuid

metadata = {
    "schemaVersion": 1,
    "units": [
        {"code": "unit", "scales": [0]}
    ],
    "divisions": [
        {
            "code": "SYNTHETIC_ELECTRONICS",
            "attributes": [
                {"key": "display", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "battery_life", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "wireless_standard", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "ports", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "accessory", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "sensitivity", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "outlets", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "battery", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "storage", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "capacity", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "refresh_rate", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "camera", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "memory", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "cable_length", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "connectivity", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "processor", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "connection", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "switches", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "resolution", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "output", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "speed", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "warranty", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "connectors", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "power_rating", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "compatibility", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "build_material", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "certification", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "importer", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "packaging", "type": "TEXT_ARRAY", "unit": None, "maximumLength": 1000},
                {"key": "carton_specs", "type": "TEXT_ARRAY", "unit": None, "maximumLength": 1000},
                {"key": "warehouse_status", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "dispatch_turnaround", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "nationwide_shipping", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "min_wholesale_order", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "features", "type": "TEXT_ARRAY", "unit": None, "maximumLength": 1000},
                {"key": "overview", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "highlights", "type": "TEXT_ARRAY", "unit": None, "maximumLength": 1000},
                {"key": "price_subtitle", "type": "TEXT", "unit": None, "maximumLength": 1000},
                {"key": "starting_price", "type": "TEXT", "unit": None, "maximumLength": 50},
                {"key": "wholesale_rate", "type": "TEXT", "unit": None, "maximumLength": 50},
                {"key": "distributor_rate", "type": "TEXT", "unit": None, "maximumLength": 50}
            ]
        }
    ]
}

val_str = json.dumps(metadata).replace("'", "''")
row_id = str(uuid.uuid4())
sql = f"""
INSERT INTO commerce.feature_configuration (id, created_by, updated_by, version, key, revision, value, active, approved_by)
VALUES ('{row_id}', 'system', 'system', 1, 'catalogue.metadata', 1, '{val_str}'::jsonb, true, 'system')
ON CONFLICT (key, revision) DO UPDATE SET value=EXCLUDED.value, active=true, updated_at=now();
"""

res = subprocess.run(
    ["docker", "exec", "-i", "nigson-local-postgres-1", "psql", "-U", "nigson_bootstrap", "-d", "nigson"],
    input=sql,
    text=True,
    capture_output=True
)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)
if res.returncode == 0:
    print("SUCCESS: catalogue.metadata seeded successfully!")
else:
    print("FAILED with code", res.returncode)

