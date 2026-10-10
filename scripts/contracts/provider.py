"""Closed API-163/164 commerce operations proxy; private worker routes remain worker-owned."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    states = ['PENDING', 'CLAIMED', 'SUCCEEDED', 'FAILED', 'UNKNOWN']
    schemas = spec['components']['schemas']
    schemas['JobDTO'] = object_schema({'id': UUID, 'state': {'type': 'string', 'enum': states}, 'attemptCount': {'type': 'integer', 'minimum': 0, 'maximum': 2147483647}, 'nextAttemptAt': {'type': 'string', 'format': 'date-time'}, 'lastErrorCode': {'anyOf': [{'type': 'string', 'pattern': '^[A-Z][A-Z0-9_]{0,99}$'}, {'type': 'null'}]}})
    schemas['JobPage'] = object_schema({'items': {'type': 'array', 'maxItems': 100, 'items': REF('JobDTO')}, 'page': {'type': 'integer', 'minimum': 0}, 'size': {'type': 'integer', 'minimum': 1, 'maximum': 100}, 'totalElements': VERSION, 'totalPages': VERSION})
    schemas['JobRetry'] = object_schema({'reason': {'type': 'string', 'minLength': 1, 'maxLength': 500, 'description': 'Nonblank and no control/format characters. Never copied into audit or errors.'}})
    read = operation('API-163', 'Current OPERATIONS without organization context. Compiled private API-140 with exact jobs.read workload scope; this registered read route rejects signed actors. Closed paginated/redacted JobDTO only. No arbitrary URL, provider payload, browser token forwarding or cross-owner SQL.', 'JobPage', [parameter('state', 'query', {'type': 'string', 'enum': states}), parameter('page', 'query', {'type': 'integer', 'minimum': 0, 'default': 0}), parameter('size', 'query', {'type': 'integer', 'minimum': 1, 'maximum': 100, 'default': 20}), parameter('sort', 'query', {'type': 'string', 'enum': ['nextAttemptAt,asc', 'nextAttemptAt,desc', 'id,asc', 'id,desc'], 'default': 'nextAttemptAt,asc'})])
    retry = operation('API-164', 'Current OPERATIONS and verified session with MFA within 15 minutes. CSRF and quoted If-Match mandatory. Commit owned command UUID/hash/audit intent before any unlocked KMS/token/upstream HTTP. API-141 uses exact jobs.retry_WITH_SIGNED_STAFF scope and assertion bound to method/path/body/correlation. Stable private command UUID across UNKNOWN acknowledgement reconciliation; upstream command dedup and provider UNKNOWN reconciliation required. Cached acknowledgements still require current authority. Never substitute a guessed job version. JobDTO has no version in the master; successful retries expose actual upstream ETag, and initial version discovery remains a documented contract gap.', 'JobDTO', [parameter('id', 'path', UUID, True), parameter('If-Match', 'header', {'type': 'string', 'pattern': '^"(0|[1-9][0-9]{0,18})"$'}, True), parameter('Idempotency-Key', 'header', {'type': 'string', 'minLength': 1, 'maxLength': 100}, True), parameter('X-CSRF-TOKEN', 'header', {'type': 'string', 'minLength': 43, 'maxLength': 43}, True)])
    retry['requestBody'] = {'required': True, 'content': {'application/json': {'schema': REF('JobRetry')}}}
    retry['responses']['202'] = retry['responses'].pop('200')
    retry['responses']['202']['headers'] = {'ETag': {'schema': {'type': 'string'}}, 'Cache-Control': {'schema': {'const': 'no-store'}}}
    read['responses']['200']['headers'] = {'Cache-Control': {'schema': {'const': 'no-store'}}}
    for op in (read, retry):
        op['responses']['422']['description'] = 'Feature disabled or provider rule violation'
        op['responses']['503'] = {'description': 'Unavailable read or uncertain retry acknowledgement. Reconcile with the identical command key; no new provider effect key.', 'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}
    spec['paths']['/api/v1/staff/integration-jobs'] = {'get': read}
    spec['paths']['/api/v1/staff/integration-jobs/{id}/retry'] = {'post': retry}
    return spec
