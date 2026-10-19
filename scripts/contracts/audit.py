"""API-134 Commerce-owned, ADMIN-only audit query contract."""

def extend(spec):
    from authorization import REF, UUID, VERSION, object_schema, operation, parameter
    nullable = lambda schema: {'anyOf': [schema, {'type': 'null'}]}
    schemas = spec['components']['schemas']
    schemas['AuditDTO'] = object_schema({
        'id': UUID,
        'actor': {'type': 'string', 'minLength': 1, 'maxLength': 200},
        'action': {'type': 'string', 'minLength': 1, 'maxLength': 100},
        'resourceType': {'type': 'string', 'minLength': 1, 'maxLength': 80},
        'resourceId': UUID,
        'occurredAt': {'type': 'string', 'format': 'date-time'},
        'reason': nullable({'type': 'string', 'maxLength': 500}),
        'oldValue': nullable({'type': 'object', 'additionalProperties': True}),
        'newValue': nullable({'type': 'object', 'additionalProperties': True}),
    })
    schemas['AuditPage'] = object_schema({
        'items': {'type': 'array', 'maxItems': 100, 'items': REF('AuditDTO')},
        'page': {'type': 'integer', 'minimum': 0},
        'size': {'type': 'integer', 'minimum': 1, 'maximum': 100},
        'totalElements': VERSION,
        'totalPages': VERSION,
    })
    op = operation('API-134', 'Current Commerce ADMIN only. Require an active mapped ADMIN assignment on every request. Read only Commerce audit_record; never expose encrypted tokens, full form values or unredacted fields. Filters are exact bound values; reject unknown/repeated parameters, bodies, invalid date ranges and non-allowlisted sorts. Stable ordering uses audit id as the final tie-breaker. Pages contain at most 100 rows. This GET does not mutate audit history and has no idempotency key, outbox event or audit-on-read side effect.', 'AuditPage', [
        parameter('resourceId', 'query', UUID),
        parameter('action', 'query', {'type': 'string', 'pattern': '^[A-Z][A-Z0-9_]{0,99}$', 'maxLength': 100}),
        parameter('from', 'query', {'type': 'string', 'format': 'date-time', 'maxLength': 40}),
        parameter('to', 'query', {'type': 'string', 'format': 'date-time', 'maxLength': 40}),
        parameter('page', 'query', {'type': 'integer', 'minimum': 0, 'default': 0}),
        parameter('size', 'query', {'type': 'integer', 'minimum': 1, 'maximum': 100, 'default': 20}),
        parameter('sort', 'query', {'type': 'string', 'enum': ['occurredAt,desc', 'occurredAt,asc', 'actor,asc', 'actor,desc', 'action,asc', 'action,desc', 'resourceType,asc', 'resourceType,desc'], 'default': 'occurredAt,desc'}),
    ])
    op['responses']['200']['headers'] = {'Cache-Control': {'schema': {'const': 'no-store'}}}
    op['responses'].pop('412', None)
    op['responses'].pop('409', None)
    op['responses'].pop('422', None)
    spec['paths']['/api/v1/commerce/staff/audit-records'] = {'get': op}
    return spec
