"""Reviewed incremental API-011/012 OpenAPI contract; JSON is a YAML subset."""
import argparse
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
ROLES = ['ADMIN', 'CONTENT_EDITOR', 'CONTENT_PUBLISHER', 'SALES', 'OPERATIONS', 'FINANCE', 'ANALYST']
REF = lambda name: {'$ref': '#/components/schemas/' + name}
UUID = {'type': 'string', 'format': 'uuid', 'pattern': r'^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$'}
VERSION = {'type': 'integer', 'format': 'int64', 'minimum': 0}
def object_schema(properties):
    return {'type': 'object', 'additionalProperties': False, 'required': list(properties), 'properties': properties}
def parameter(name, location, schema, required=False):
    return {'name': name, 'in': location, 'required': required, 'schema': schema}
def operation(api, description, response, parameters):
    return {'operationId': api, 'description': description, 'security': [{'bffSession': []}], 'parameters': parameters,
            'responses': {'200': {'description': 'Success', 'content': {'application/json': {'schema': REF(response)}}},
                          **{str(code): {'description': meaning, 'content': {'application/problem+json': {'schema': {'$ref': '../problem/problem.schema.json'}}}}
                             for code, meaning in [(400, 'Invalid input'), (401, 'Authentication required'), (403, 'Access denied'), (404, 'Not found'), (409, 'Conflicting or in-progress request'), (412, 'Version mismatch'), (422, 'Last active administrator protection'), (415, 'Unsupported content type'), (500, 'Safe internal error')]}}}
def contract():
    roles = {'type': 'array', 'maxItems': 7, 'uniqueItems': True, 'items': REF('Role')}
    get = operation('API-011', 'ADMIN only. Search mapped staff, including historical assignments. Reject unknown or repeated parameters. Default stable createdAt descending order. No request body.', 'StaffPage', [
        parameter('q', 'query', {'type': 'string', 'maxLength': 200, 'default': ''}),
        parameter('page', 'query', {'type': 'integer', 'minimum': 0, 'default': 0}),
        parameter('size', 'query', {'type': 'integer', 'minimum': 1, 'maximum': 100, 'default': 20}),
        parameter('sort', 'query', {'type': 'string', 'enum': ['createdAt,desc', 'createdAt,asc', 'displayName,asc', 'displayName,desc', 'email,asc', 'email,desc'], 'default': 'createdAt,desc'})])
    put = operation('API-012', 'Current ADMIN and signed recent MFA within 15 minutes. Active mapped staff target; preserve last active ADMIN. StaffDTO version is the Party aggregate version. Audit changes, increment revision and revoke all target sessions in one transaction. Idempotency identity is actor party plus API-012; canonical input includes target, expected revision, roles and reason. Matching replay returns original response; conflicting input is 409; stale revision is 412. No-op preserves revision and sessions. Reason is validated but free text is redacted from audit under existing policy. No direct domain event.', 'StaffDTO', [
        parameter('id', 'path', UUID, True),
        parameter('If-Match', 'header', {'type': 'string', 'pattern': '^"(0|[1-9][0-9]{0,18})"$'}, True),
        parameter('Idempotency-Key', 'header', {'type': 'string', 'minLength': 1, 'maxLength': 100, 'pattern': '^[ -~]+$'}, True),
        parameter('X-CSRF-TOKEN', 'header', {'type': 'string', 'minLength': 43, 'maxLength': 43}, True)])
    put['requestBody'] = {'required': True, 'content': {'application/json': {'schema': REF('StaffRoleChange')}}}
    put['responses']['200']['headers'] = {'ETag': {'description': 'Quoted aggregate revision', 'schema': {'type': 'string'}}}
    result = {'openapi': '3.1.0', 'info': {'title': 'Nigson commerce implemented authorization contracts', 'version': '1.1.0', 'description': 'Incremental implemented endpoints; pending roadmap APIs remain in docs/API_CONTRACT.md.'},
            'paths': {'/api/v1/staff/users': {'get': get}, '/api/v1/staff/users/{id}/roles': {'put': put}},
            'components': {'securitySchemes': {'bffSession': {'type': 'apiKey', 'in': 'cookie', 'name': '__Host-nigson-session', 'description': 'Encrypted JDBC BFF session. Explicit insecure local profile uses nigson-local-session. Browser bearer tokens are denied by SEC-001.'}},
                           'schemas': {'Role': {'type': 'string', 'enum': ROLES},
                                       'StaffDTO': object_schema({'id': UUID, 'displayName': {'type': 'string', 'maxLength': 160}, 'email': {'type': 'string', 'format': 'email', 'maxLength': 254}, 'roles': roles, 'version': VERSION}),
                                       'StaffRoleChange': object_schema({'roles': roles, 'reason': {'type': 'string', 'minLength': 10, 'maxLength': 500, 'description': 'At least 10 non-padding characters; no control characters.'}}),
                                       'StaffPage': object_schema({'items': {'type': 'array', 'maxItems': 100, 'items': REF('StaffDTO')}, 'page': {'type': 'integer', 'minimum': 0}, 'size': {'type': 'integer', 'minimum': 1, 'maximum': 100}, 'totalElements': VERSION, 'totalPages': VERSION})}}}
    from party import extend
    from session import extend as session_extend
    return session_extend(extend(result))
def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true'); args = parser.parse_args()
    target = ROOT / 'contracts/openapi/commerce.yaml'; expected = json.dumps(contract(), indent=2) + '\n'
    if args.check:
        if not target.exists() or target.read_text() != expected: raise SystemExit('FAIL: reviewed authorization OpenAPI drift')
        print('PASS: API-001–012 generated OpenAPI matches reviewed contract')
    else:
        target.parent.mkdir(parents=True, exist_ok=True); target.write_text(expected)
if __name__ == '__main__': main()
