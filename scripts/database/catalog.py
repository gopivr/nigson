#!/usr/bin/env python3
"""Read the normative section-10 catalog; reject unrecognized field notation."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def catalog():
    source = (ROOT / 'BACKEND_IMPLEMENTATION_MASTER.md').read_text()
    section = source[source.index('# 10. Complete Database Schema'):source.index('# 11. Liquibase Architecture')]
    tables = {}
    for name, body in re.findall(r'^### ([a-z]+\.[a-z_]+)\n(.*?)(?=^### |^## |\Z)', section, re.M | re.S):
        metadata = re.search(r'Owner: `([^`]+)`; module: `([^`]+)`; introduced: BP(\d); mutation: ([\w-]+)', body)
        fields = []
        for line in re.search(r'```text\n(.*?)```', body, re.S)[1].strip().splitlines():
            match = re.fullmatch(r'(\w+) (\w+(?:\(\d+(?:,\d+)?\))?) (NN|NULL)(.*)', line)
            if not match:
                raise ValueError(f'Unrecognized field notation: {name}: {line}')
            field, datatype, null, attributes = match.groups()
            default = re.search(r' DEFAULT (.*?)(?= FK | EXTERNAL |$)', attributes)
            relation = re.search(r' FK ([\w.]+)(.*)', attributes)
            external = re.search(r' EXTERNAL ([\w.]+)', attributes)
            fields.append({'name': field, 'type': datatype, 'nullable': null == 'NULL',
                           'primary': bool(re.search(r'\bPK\b', attributes)),
                           'default': default[1] if default else None,
                           'foreign': relation[1] if relation else None,
                           'cascade': bool(relation and 'ON DELETE CASCADE' in relation[2]),
                           'external': external[1] if external else None})
        tables[name] = {'owner': metadata[1], 'module': metadata[2], 'phase': int(metadata[3]),
                        'mutation': metadata[4], 'fields': fields,
                        'keys': re.search(r'\*\*Keys and indexes:\*\* (.*)', body)[1].rstrip('.'),
                        'checks': re.search(r'\*\*Checks:\*\* (.*)', body)[1].rstrip('.')}
    tables['commerce.cart']['creation_changeset']='commerce-20261018-001'
    tables['commerce.cart']['creation_file']='services/commerce/src/main/resources/db/changelog/043-bp3-quote-cart.sql'
    if not tables:
        raise ValueError('Missing normative table catalog')
    return tables


if __name__ == '__main__':
    print(json.dumps(catalog(), indent=2))
