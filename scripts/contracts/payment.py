"""Owned payment verification, authenticated receipts and financial refund contracts."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    s = spec['components']['schemas']
    nullable = lambda value: {'anyOf': [value, {'type': 'null'}]}
    money = {'type': 'string', 'pattern': r'^[0-9]{1,15}\.[0-9]{2}$'}
    amount = {'type': 'string', 'pattern': r'^[0-9]{1,15}(\.[0-9]{1,2})?$'}
    text = lambda maximum: {'type': 'string', 'minLength': 1, 'maxLength': maximum}
    refund = {'id': UUID, 'state': {'enum': ['REQUESTED', 'APPROVED', 'SUBMITTING', 'PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'REJECTED']}, 'amount': money, 'currency': {'const': 'NGN'}, 'version': VERSION}
    s['PaymentDTO'] = object_schema({'orderId': UUID, 'state': {'enum': ['CREATED', 'INITIATING', 'PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'CANCELLED']}, 'settled': {'type': 'boolean'}, 'reference': text(100), 'checkoutUrl': nullable({'type': 'string', 'format': 'uri', 'pattern': '^https://', 'maxLength': 1000})})
    s['RefundDTO'] = object_schema(refund)
    s['RefundDetailDTO'] = object_schema({**refund, 'providerReference': nullable(text(120))})
    s['RefundLineInput'] = object_schema({'orderLineId': UUID, 'quantity': {'type': 'string', 'pattern': r'^[0-9]{1,17}(\.[0-9]{1,3})?$'}, 'amount': amount})
    s['RefundInput'] = object_schema({'amount': amount, 'reason': {'type': 'string', 'minLength': 10, 'maxLength': 500}, 'lines': {'type': 'array', 'minItems': 1, 'maxItems': 100, 'items': REF('RefundLineInput')}})
    s['RefundInput']['required'].remove('lines')
    s['RefundApprovalInput'] = object_schema({'approve': {'type': 'boolean'}, 'reason': text(500)})
    s['PaystackReceiptDTO'] = object_schema({'received': {'type': 'boolean'}})
    s['PaymentReconciliationInput'] = object_schema({'jobId': UUID, 'limit': {'type': 'integer', 'minimum': 1, 'maximum': 100}, 'cursor': {'type': 'string', 'pattern': '^[A-Za-z0-9_-]{1,256}$'}})
    s['PaymentReconciliationInput']['required'].remove('cursor')
    s['PaymentReconciliationDTO'] = object_schema({'processed': {'type': 'integer', 'minimum': 0, 'maximum': 100}, 'nextCursor': nullable(text(256)), 'mismatches': {'type': 'integer', 'minimum': 0, 'maximum': 100}})
    spec['components']['securitySchemes']['paystackSignature'] = {'type': 'apiKey', 'in': 'header', 'name': 'X-Paystack-Signature', 'description': 'HMAC-SHA512 of exact raw bytes, verified before parsing. Independent provider verification authorizes financial state.'}
    spec['components']['securitySchemes']['paymentIntegrationWorkload'] = {'type': 'oauth2', 'flows': {'clientCredentials': {'tokenUrl': '/realms/nigson/protocol/openid-connect/token', 'scopes': {'payment.reconcile': 'Commerce-owned independent verification'}}}, 'description': 'Private ingress; exact nigson-integration client, issuer, audience and scope. No browser cookie or signed actor.'}
    path = parameter('id', 'path', UUID, True)
    key = parameter('Idempotency-Key', 'header', text(100), True)
    csrf = parameter('X-CSRF-TOKEN', 'header', {'type': 'string', 'minLength': 43, 'maxLength': 43}, True)
    match = parameter('If-Match', 'header', {'type': 'string', 'pattern': '^"(0|[1-9][0-9]{0,18})"$'}, True)
    defs = [
        ('/api/v1/orders/{id}/payment', 'get', 'API-041', None, 'PaymentDTO', 200, [path], 'Current customer owner/active organization membership. Local independently verified financial truth only; no provider polling.'),
        ('/api/v1/webhooks/payments/paystack', 'post', 'API-042', None, 'PaystackReceiptDTO', 200, [parameter('X-Paystack-Signature', 'header', {'type': 'string', 'pattern': '^[a-fA-F0-9]{128}$'}, True)], 'Raw JSON at most256KiB. Authenticate before parsing; encrypt and commit receipt before200. Stable event dedup; changed bodies separately quarantined. Receipt acknowledgement does not assert financial success.'),
        ('/api/v1/staff/orders/{id}/refunds', 'post', 'API-052', 'RefundInput', 'RefundDTO', 201, [path, key, csrf, match], 'Current FINANCE and recent MFA. If-Match is order version. Positive amount and immutable distinct line caps; reserve all requested/in-flight/successful value under order lock. Refund/audit/EVT-010 commit atomically; no provider HTTP.'),
        ('/api/v1/staff/refunds/{id}/approval', 'post', 'API-053', 'RefundApprovalInput', 'RefundDTO', 200, [path, key, csrf, match], 'Current FINANCE and recent MFA. If-Match is refund version. Separate requester/approver by default; rejection releases only financial reservation. Approval/audit/EVT-011 commit before durable provider dispatch. Replay reveals existing resource state.'),
        ('/api/v1/staff/refunds/{id}', 'get', 'API-054', None, 'RefundDetailDTO', 200, [path], 'Current FINANCE and recent MFA. Actual bound provider reference, nullable until known; no credentials. Detail extends five-field RefundDTO per CF-PAYMENT-001.'),
        ('/internal/v1/payments/reconciliation', 'post', 'API-118', 'PaymentReconciliationInput', 'PaymentReconciliationDTO', 202, [key], 'Exact private integration/payment.reconcile. Durable canonical intent and immutable paged plan precede provider HTTP; final response survives replay. Commerce keeps credentials. processed counts verified work attempts; mismatches counts rejected callback evidence. UNKNOWN remains monitored, never blindly resubmitted.')]
    for route, method, api, body, response, status, params, description in defs:
        op = operation(api, description, response, params)
        if body: op['requestBody'] = {'required': True, 'content': {'application/json': {'schema': REF(body)}}}
        if api == 'API-042':
            op['security'] = [{'paystackSignature': []}]
            op['requestBody'] = {'required': True, 'content': {'application/json': {'schema': {'type': 'object'}}}}
            op['x-raw-body-max-bytes'] = 262144
        if api == 'API-118': op['security'] = [{'paymentIntegrationWorkload': ['payment.reconcile']}]
        if status != 200: op['responses'][str(status)] = op['responses'].pop('200')
        op['responses'][str(status)]['headers'] = {'Cache-Control': {'schema': {'const': 'no-store'}}}
        if response in {'RefundDTO', 'RefundDetailDTO'}: op['responses'][str(status)]['headers']['ETag'] = {'schema': {'type': 'string'}}
        op['responses']['422']['description'] = 'PAYMENT_RULE_VIOLATION or disabled/mismatched activation'
        op['responses']['413'] = {'description': 'Body exceeds allowed byte limit', 'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}
        op['responses']['503'] = {'description': 'Safe provider or owned persistence unavailable', 'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}
        spec['paths'][route] = {method: op}
    return spec
