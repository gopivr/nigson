"""Commerce API-159 private cart recovery eligibility contract."""

def extend(spec):
    from authorization import object_schema, operation, parameter, REF, UUID
    schemas = spec['components']['schemas']
    schemas['CartRecoveryEligibility'] = object_schema({
        'eligible': {'type': 'boolean'},
        'partyId': UUID,
        'cartLink': {'anyOf': [{'type': 'string', 'format': 'uri', 'pattern': '^https://'}, {'type': 'null'}]},
    })
    op = operation(
        'API-159',
        'Exact nigson-integration recovery.read workload; no signed actor, browser session or query. Empty JSON body only. Reads the current Commerce-owned ACTIVE cart under lock, requires nonempty lines, at least 24 hours inactivity and no linked order or later paid context order. An eligible result records one durable Commerce abandoned-cart candidate using a stable per-cart key and the contract default eligibility time. This endpoint does not inspect consent, mint a recovery link, contact a customer, send a message, expire a cart or apply campaign/retention policy; Integration must independently recheck consent before any separately configured send. cartLink is null until an approved HTTPS link is configured.',
        'CartRecoveryEligibility', [parameter('id', 'path', UUID, True)])
    op['security'] = [{'cartRecoveryWorkload': ['recovery.read']}]
    op['requestBody'] = {'required': True, 'content': {'application/json': {'schema': object_schema({})}}}
    op['responses']['200']['headers'] = {'Cache-Control': {'schema': {'const': 'no-store'}}}
    op['responses'].pop('412', None)
    op['responses'].pop('409', None)
    op['responses'].pop('422', None)
    spec['components']['securitySchemes']['cartRecoveryWorkload'] = {
        'type': 'oauth2',
        'flows': {'clientCredentials': {'tokenUrl': '/realms/nigson/protocol/openid-connect/token', 'scopes': {'recovery.read': 'Read current Commerce cart recovery eligibility'}}},
        'description': 'Exact nigson-integration client, Commerce audience, recovery.read scope and private ingress; no browser session or signed user actor.'}
    spec['paths']['/internal/v1/cart-recovery/{id}/eligibility'] = {'post': op}
    return spec
