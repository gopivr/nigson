"""Reviewed account wishlist API-216/217/218 (ADR-029)."""

PATHS = {'/api/v1/me/wishlist': {'get': {'operationId': 'API-216',
                                 'description': 'Current active CUSTOMER in personal context only. Account '
                                                'derived from trusted session, never supplied by client. No '
                                                'request body or query parameters. No OTP. Cache-Control: '
                                                'no-store. At most 100 unique SKUs per account. Mutations '
                                                'require CSRF and Idempotency-Key, serialize on current '
                                                'party and atomically audit only actual changes. No '
                                                'If-Match: set-membership operations, not full-list '
                                                'replacement. Same operation/key replays original '
                                                'acknowledgement; reload GET for current membership. No '
                                                'provider calls or domain events. Ordered savedAt descending '
                                                'then skuId; unavailable saved SKUs remain removable without '
                                                'exposing unpublished metadata. available means NG catalogue '
                                                'eligibility, not inventory.',
                                 'security': [{'bffSession': []}],
                                 'responses': {'200': {'description': 'Current account wishlist',
                                                       'headers': {'Cache-Control': {'schema': {'type': 'string',
                                                                                                'const': 'no-store'}}},
                                                       'content': {'application/json': {'schema': {'type': 'array',
                                                                                                   'maxItems': 100,
                                                                                                   'items': {'$ref': '#/components/schemas/WishlistItem'}}}}},
                                               '400': {'description': 'Invalid input',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '401': {'description': 'Authentication required',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '403': {'description': 'Access denied',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '404': {'description': 'SKU unavailable',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '409': {'description': 'Idempotency conflict or in progress',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '422': {'description': 'Wishlist limit reached (100 items)',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                               '500': {'description': 'Internal error',
                                                       'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}}}},
 '/api/v1/me/wishlist/items/{skuId}': {'put': {'operationId': 'API-217',
                                               'description': 'Current active CUSTOMER in personal context '
                                                              'only. Account derived from trusted session, '
                                                              'never supplied by client. No request body or '
                                                              'query parameters. No OTP. Cache-Control: '
                                                              'no-store. At most 100 unique SKUs per '
                                                              'account. Mutations require CSRF and '
                                                              'Idempotency-Key, serialize on current party '
                                                              'and atomically audit only actual changes. No '
                                                              'If-Match: set-membership operations, not '
                                                              'full-list replacement. Same operation/key '
                                                              'replays original acknowledgement; reload GET '
                                                              'for current membership. No provider calls or '
                                                              'domain events. Saves an active eligible SKU. '
                                                              'Existing eligible membership succeeds without '
                                                              'another row/audit.',
                                               'security': [{'bffSession': []}],
                                               'parameters': [{'name': 'skuId',
                                                               'in': 'path',
                                                               'required': True,
                                                               'schema': {'type': 'string',
                                                                          'format': 'uuid'}},
                                                              {'name': 'Idempotency-Key',
                                                               'in': 'header',
                                                               'required': True,
                                                               'schema': {'type': 'string',
                                                                          'minLength': 1,
                                                                          'maxLength': 100}},
                                                              {'name': 'X-CSRF-TOKEN',
                                                               'in': 'header',
                                                               'required': True,
                                                               'schema': {'type': 'string'}}],
                                               'responses': {'204': {'description': 'Membership operation '
                                                                                    'acknowledged; read GET '
                                                                                    'for current state',
                                                                     'headers': {'Cache-Control': {'schema': {'type': 'string',
                                                                                                              'const': 'no-store'}}}},
                                                             '400': {'description': 'Invalid input',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '401': {'description': 'Authentication required',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '403': {'description': 'Access denied',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '404': {'description': 'SKU unavailable',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '409': {'description': 'Idempotency conflict or '
                                                                                    'in progress',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '422': {'description': 'Wishlist limit reached '
                                                                                    '(100 items)',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                             '500': {'description': 'Internal error',
                                                                     'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}}},
                                       'delete': {'operationId': 'API-218',
                                                  'description': 'Current active CUSTOMER in personal '
                                                                 'context only. Account derived from trusted '
                                                                 'session, never supplied by client. No '
                                                                 'request body or query parameters. No OTP. '
                                                                 'Cache-Control: no-store. At most 100 '
                                                                 'unique SKUs per account. Mutations require '
                                                                 'CSRF and Idempotency-Key, serialize on '
                                                                 'current party and atomically audit only '
                                                                 'actual changes. No If-Match: '
                                                                 'set-membership operations, not full-list '
                                                                 'replacement. Same operation/key replays '
                                                                 'original acknowledgement; reload GET for '
                                                                 'current membership. No provider calls or '
                                                                 'domain events. Removes only this account '
                                                                 'association; absent or unavailable '
                                                                 'membership removal succeeds.',
                                                  'security': [{'bffSession': []}],
                                                  'parameters': [{'name': 'skuId',
                                                                  'in': 'path',
                                                                  'required': True,
                                                                  'schema': {'type': 'string',
                                                                             'format': 'uuid'}},
                                                                 {'name': 'Idempotency-Key',
                                                                  'in': 'header',
                                                                  'required': True,
                                                                  'schema': {'type': 'string',
                                                                             'minLength': 1,
                                                                             'maxLength': 100}},
                                                                 {'name': 'X-CSRF-TOKEN',
                                                                  'in': 'header',
                                                                  'required': True,
                                                                  'schema': {'type': 'string'}}],
                                                  'responses': {'204': {'description': 'Membership operation '
                                                                                       'acknowledged; read '
                                                                                       'GET for current '
                                                                                       'state',
                                                                        'headers': {'Cache-Control': {'schema': {'type': 'string',
                                                                                                                 'const': 'no-store'}}}},
                                                                '400': {'description': 'Invalid input',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '401': {'description': 'Authentication '
                                                                                       'required',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '403': {'description': 'Access denied',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '404': {'description': 'SKU unavailable',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '409': {'description': 'Idempotency conflict '
                                                                                       'or in progress',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '422': {'description': 'Wishlist limit '
                                                                                       'reached (100 items)',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}},
                                                                '500': {'description': 'Internal error',
                                                                        'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}}}}}

ITEM = {'type': 'object',
 'additionalProperties': False,
 'required': ['skuId', 'productId', 'savedAt', 'available'],
 'properties': {'skuId': {'type': 'string', 'format': 'uuid'},
                'productId': {'type': 'string', 'format': 'uuid'},
                'savedAt': {'type': 'string', 'format': 'date-time'},
                'available': {'type': 'boolean'}}}

def extend(spec):
    spec["paths"] = {**PATHS, **spec["paths"]}
    spec["components"]["schemas"] = {"WishlistItem": ITEM, **spec["components"]["schemas"]}
    return spec
