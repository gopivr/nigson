"""Reviewed closed owner configuration API-135 and non-secret metadata contract."""


def extend(spec):
    from authorization import object_schema, parameter, operation, REF, VERSION
    schemas = spec['components']['schemas']
    schemas['ConfigurationDTO'] = object_schema({'key': {'type': 'string', 'enum': ['catalogue.metadata']}, 'revision': {'type': 'integer', 'format': 'int64', 'minimum': 1}, 'active': {'type': 'boolean'}, 'version': VERSION})
    schemas['CatalogueUnitDefinition'] = object_schema({'code': {'type': 'string', 'pattern': '^[A-Za-z][A-Za-z0-9_-]{0,19}$'}, 'scales': {'type': 'array', 'minItems': 1, 'maxItems': 4, 'uniqueItems': True, 'items': {'type': 'integer', 'minimum': 0, 'maximum': 3}}})
    schemas['CatalogueAttributeDefinition'] = object_schema({'key': {'type': 'string', 'pattern': '^[a-z][a-z0-9_]{0,79}$'}, 'type': {'type': 'string', 'enum': ['TEXT', 'DECIMAL', 'BOOLEAN', 'TEXT_ARRAY', 'DECIMAL_ARRAY', 'BOOLEAN_ARRAY']}, 'unit': {'type': ['string', 'null'], 'pattern': '^[A-Za-z][A-Za-z0-9_-]{0,19}$'}, 'maximumLength': {'type': 'integer', 'minimum': 1, 'maximum': 1000}})
    schemas['CatalogueDivisionDefinition'] = object_schema({'code': {'type': 'string', 'pattern': '^[A-Za-z][A-Za-z0-9_-]{0,39}$'}, 'attributes': {'type': 'array', 'maxItems': 50, 'items': REF('CatalogueAttributeDefinition')}})
    schemas['CatalogueMetadata'] = object_schema({'schemaVersion': {'type': 'integer', 'const': 1}, 'units': {'type': 'array', 'maxItems': 100, 'items': REF('CatalogueUnitDefinition')}, 'divisions': {'type': 'array', 'maxItems': 100, 'items': REF('CatalogueDivisionDefinition')}})
    schemas['ConfigurationChange'] = object_schema({'value': REF('CatalogueMetadata'), 'active': {'type': 'boolean'}, 'reason': {'type': 'string', 'minLength': 10, 'maxLength': 500, 'description': 'NFC, at least ten non-padding characters, no control characters; free text is not retained in audit.'}})
    put = operation('API-135', 'Current commerce ADMIN with no organization context. Cookie CSRF required. Only registered non-secret key catalogue.metadata. Unknown fields/keys and query parameters are rejected. If-Match 0 creates an absent key at revision/version 1; later commands require its latest version. Each successful command appends immutable history and redacted audit atomically, returning ETag. Durable idempotency includes key, expected version and exact body; replay checks current authorization and returns the original response. Active revisions require signed MFA within 15 minutes and metadata compatible with existing SKU units/scales and product specifications. Latest inactive revision disables use even if historical revisions were active. No domain event or provider call; actual business metadata is not seeded.', 'ConfigurationDTO', [
        parameter('key', 'path', {'type': 'string', 'enum': ['catalogue.metadata']}, True),
        parameter('If-Match', 'header', {'type': 'string', 'pattern': '^"(0|[1-9][0-9]{0,18})"$'}, True),
        parameter('Idempotency-Key', 'header', {'type': 'string', 'minLength': 1, 'maxLength': 100, 'pattern': '^[ -~]+$'}, True),
        parameter('X-CSRF-TOKEN', 'header', {'type': 'string', 'minLength': 43, 'maxLength': 43}, True)])
    put['responses']['422']['description'] = 'Unregistered/invalid owner policy or feature unavailable'
    put['requestBody'] = {'required': True, 'content': {'application/json': {'schema': REF('ConfigurationChange')}}}
    put['responses']['200']['headers'] = {'ETag': {'description': 'Quoted latest key version', 'schema': {'type': 'string'}}}
    spec['paths']['/api/v1/commerce/staff/configuration/{key}'] = {'put': put}
    return spec
