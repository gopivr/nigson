"""Implemented API-005–010 additions; composed with existing authorization contract."""
from copy import deepcopy

def extend(spec):
    from authorization import UUID, VERSION, REF, object_schema, parameter, operation
    string = lambda n: {'type': 'string', 'minLength': 1, 'maxLength': n}
    nullable = lambda value: {'anyOf': [value, {'type': 'null'}]}
    fields = {'label': nullable(string(60)), 'recipient': string(160), 'phone': string(32), 'line1': string(200), 'line2': nullable(string(200)), 'city': string(100), 'region': nullable(string(100)), 'postalCode': nullable(string(24)), 'countryCode': {'type': 'string', 'pattern': '^[A-Z]{2}$', 'description': 'Assigned ISO 3166 alpha-2 country with an enabled, consistent market destination policy.'}, 'organizationId': nullable(UUID), 'isDefault': {'type': 'boolean'}}
    profile = {'id': UUID, 'displayName': string(160), 'email': {'type': 'string', 'format': 'email', 'maxLength': 254}, 'emailVerified': {'type': 'boolean'}, 'phone': nullable(string(32)), 'locale': string(16), 'version': VERSION}
    schemas = spec['components']['schemas']
    schemas['ProfileDTO'] = object_schema(profile)
    schemas['AddressDTO'] = object_schema({'id': UUID, **fields, 'version': VERSION})
    schemas['AddressList'] = {'type': 'array', 'maxItems': 100, 'items': REF('AddressDTO')}
    schemas['ProfilePatch'] = {'type': 'object', 'additionalProperties': False, 'minProperties': 1, 'properties': {k: profile[k] for k in ('displayName', 'phone', 'locale')}}
    schemas['AddressInput'] = {'type': 'object', 'additionalProperties': False, 'required': ['recipient', 'phone', 'line1', 'city', 'countryCode'], 'properties': deepcopy(fields)}
    schemas['AddressPatch'] = {'type': 'object', 'additionalProperties': False, 'minProperties': 1, 'properties': deepcopy(fields)}
    schemas['OrganizationMembershipDTO'] = object_schema({'id':UUID,'name':string(200),'membershipRole':{'type':'string','enum':['OWNER','BUYER']},'entitlementState':{'type':'string','enum':['PENDING','ACTIVE','SUSPENDED','REVOKED']}})
    schemas['OrganizationMembershipList'] = {'type':'array','maxItems':100,'items':REF('OrganizationMembershipDTO')}
    key = parameter('Idempotency-Key', 'header', {'type': 'string', 'minLength': 1, 'maxLength': 100, 'pattern': '^[ -~]+$'}, True)
    csrf = parameter('X-CSRF-TOKEN', 'header', {'type': 'string', 'minLength': 43, 'maxLength': 43}, True)
    revision = parameter('If-Match', 'header', {'type': 'string', 'pattern': '^"(0|[1-9][0-9]{0,18})"$'}, True)
    def op(api, dto, params, input_schema=None, status='200'):
        result = operation(api, 'Current CUSTOMER and owned Party. Reject queries, unknown fields and coercion. Profile email and ownership are immutable. Organization addresses require current active membership and BP3. Mutations use durable actor/API idempotency and audit in the same local transaction; If-Match is a quoted Party or Address revision. Address default changes serialize on the owner Party. Delete soft deletes without changing historical snapshots. No provider HTTP or domain event.', dto, params)
        result['responses']['422']['description'] = 'Current profile or country address rules violated'
        result['responses']['409']['description'] = 'Conflicting or in-progress request'
        if input_schema: result['requestBody'] = {'required': True, 'content': {'application/json': {'schema': REF(input_schema)}}}
        if status != '200': result['responses'][status] = result['responses'].pop('200')
        if status == '204': result['responses'][status] = {'description': 'Soft deletion complete; no response body'}
        elif input_schema: result['responses'][status]['headers'] = {'ETag': {'schema': {'type': 'string'}, 'description': 'Quoted current revision'}}
        if status == '201': result['responses'][status]['headers']['Location'] = {'schema': {'type': 'string'}, 'description': 'Owned address resource path'}
        return result
    spec['paths']['/api/v1/me'] = {'get': op('API-005', 'ProfileDTO', []), 'patch': op('API-006', 'ProfileDTO', [revision,key,csrf], 'ProfilePatch')}
    organizations=op('API-066','OrganizationMembershipList',[])
    organizations['description']='Current CUSTOMER only. Return only this verified party’s active Commerce organization memberships, with the configured distributor entitlement state (PENDING when no canonical entitlement is configured or no owned record exists). Suspended entitlements remain visible as SUSPENDED while buying authority is denied. No organization identifier can be supplied by the caller; reject all query parameters and request bodies.'
    organizations['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    organizations['responses']['404']['description']='No current party membership or organization resource'
    spec['paths']['/api/v1/me/organizations']={'get':organizations}
    spec['paths']['/api/v1/me/addresses'] = {'get': op('API-007', 'AddressList', []), 'post': op('API-008', 'AddressDTO', [key,csrf], 'AddressInput', '201')}
    spec['paths']['/api/v1/me/addresses/{id}'] = {'patch': op('API-009', 'AddressDTO', [parameter('id','path',UUID,True),revision,key,csrf], 'AddressPatch'), 'delete': op('API-010', 'AddressDTO', [parameter('id','path',UUID,True),revision,key,csrf], status='204')}
    return spec
