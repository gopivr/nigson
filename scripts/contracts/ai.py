"""Generate/check the reviewed AI evaluation OpenAPI subset."""

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def contract():
    schemas = {
        "Case": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "id",
                "input",
                "market",
                "locale",
                "expectedIntent",
                "requiredSourceIds",
                "mustEscalate",
                "forbiddenActions",
            ],
            "properties": {
                "id": {"type": "string", "maxLength": 80},
                "input": {"type": "string", "minLength": 1, "maxLength": 2000},
                "market": {"type": "string", "maxLength": 32},
                "locale": {
                    "type": "string",
                    "maxLength": 35,
                    "pattern": "^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$",
                },
                "expectedIntent": {"type": "string", "maxLength": 80},
                "requiredSourceIds": {
                    "type": "array",
                    "maxItems": 100,
                    "items": {"type": "string", "format": "uuid"},
                },
                "mustEscalate": {"type": "boolean"},
                "forbiddenActions": {
                    "type": "array",
                    "maxItems": 100,
                    "items": {"type": "string", "maxLength": 80},
                },
            },
        },
        "Thresholds": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "groundednessMinimum",
                "scopeLeaksMaximum",
                "injectionMutationMaximum",
                "latencyP95MaximumMs",
            ],
            "properties": {
                "groundednessMinimum": {"type": "number", "minimum": 0, "maximum": 1},
                "scopeLeaksMaximum": {"type": "integer", "const": 0},
                "injectionMutationMaximum": {"type": "integer", "const": 0},
                "latencyP95MaximumMs": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 20000,
                },
            },
        },
        "CreateSuite": {
            "type": "object",
            "additionalProperties": False,
            "required": ["code", "cases", "thresholds"],
            "properties": {
                "code": {"type": "string", "maxLength": 80},
                "cases": {
                    "type": "array",
                    "minItems": 200,
                    "maxItems": 500,
                    "items": {"$ref": "#/components/schemas/Case"},
                },
                "thresholds": {"$ref": "#/components/schemas/Thresholds"},
            },
        },
        "SuiteCreated": {
            "type": "object",
            "additionalProperties": False,
            "required": ["id", "revision"],
            "properties": {
                "id": {"type": "string", "format": "uuid"},
                "revision": {"type": "integer", "minimum": 1},
            },
        },
        "RunEvaluation": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "suiteId",
                "modelPolicyRevision",
                "promptRevision",
                "retrievalRevision",
            ],
            "properties": {
                "suiteId": {"type": "string", "format": "uuid"},
                "modelPolicyRevision": {"type": "string", "maxLength": 80},
                "promptRevision": {"type": "string", "maxLength": 40},
                "retrievalRevision": {"type": "string", "maxLength": 40},
            },
        },
        "EvaluationQueued": {
            "type": "object",
            "additionalProperties": False,
            "required": ["evaluationId", "state"],
            "properties": {
                "evaluationId": {"type": "string", "format": "uuid"},
                "state": {"const": "QUEUED"},
            },
        },
        "ModelParameters": {
            "type": "object",
            "additionalProperties": False,
            "required": ["schemaVersion", "temperature", "maxOutputTokens", "requestDeadlineSeconds", "maximumSteps", "maximumTools", "maximumInputTokens", "concurrency", "dailyBudget", "rateRevision"],
            "properties": {
                "schemaVersion": {"const": 1},
                "temperature": {"type": "number", "minimum": 0, "maximum": 1},
                "maxOutputTokens": {"type": "integer", "minimum": 128, "maximum": 2000},
                "requestDeadlineSeconds": {"type": "integer", "minimum": 1, "maximum": 20},
                "maximumSteps": {"type": "integer", "minimum": 1, "maximum": 8},
                "maximumTools": {"type": "integer", "minimum": 0, "maximum": 4},
                "maximumInputTokens": {"type": "integer", "minimum": 1, "maximum": 12000},
                "concurrency": {"type": "integer", "minimum": 1, "maximum": 10},
                "dailyBudget": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "^[0-9]+(\\.[0-9]+)?$"},
                "rateRevision": {"type": "integer", "minimum": 1},
            },
        },
        "CreateModelPolicy": {
            "type": "object",
            "additionalProperties": False,
            "required": ["code", "provider", "chatModelId", "parameters", "promptRevisionId"],
            "properties": {
                "code": {"type": "string", "maxLength": 80},
                "provider": {"const": "BEDROCK"},
                "chatModelId": {"type": "string", "maxLength": 200},
                "fallbackModelId": {"type": ["string", "null"], "maxLength": 200},
                "parameters": {"$ref": "#/components/schemas/ModelParameters"},
                "promptRevisionId": {"type": "string", "format": "uuid"},
            },
        },
        "PolicyCreated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "revision", "approved", "version"],
            "properties": {"id": {"type": "string", "format": "uuid"}, "revision": {"type": "integer", "minimum": 1}, "approved": {"const": False}, "version": {"type": "integer", "minimum": 0}},
        },
        "CreatePromptRevision": {
            "type": "object", "additionalProperties": False,
            "required": ["code", "template", "toolSchemaRevision", "reason"],
            "properties": {"code": {"type": "string", "maxLength": 80}, "template": {"type": "string", "minLength": 1, "maxLength": 20000}, "toolSchemaRevision": {"type": "string", "maxLength": 80}, "reason": {"type": "string", "maxLength": 500}},
        },
        "PromptRevisionCreated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "code", "revision", "checksum"],
            "properties": {"id": {"type": "string", "format": "uuid"}, "code": {"type": "string"}, "revision": {"type": "integer", "minimum": 1}, "checksum": {"type": "string", "pattern": "^[0-9a-f]{64}$"}},
        },
        "CreateModelRate": {
            "type": "object", "additionalProperties": False,
            "required": ["provider", "modelId", "inputPerMillion", "outputPerMillion", "currency", "effectiveFrom", "approvalReference"],
            "properties": {"provider": {"const": "BEDROCK"}, "modelId": {"type": "string", "maxLength": 200}, "inputPerMillion": {"type": "string", "pattern": "^(0|[1-9][0-9]{0,12})(\\.[0-9]{1,6})?$"}, "outputPerMillion": {"type": "string", "pattern": "^(0|[1-9][0-9]{0,12})(\\.[0-9]{1,6})?$"}, "currency": {"type": "string", "pattern": "^[A-Z]{3}$"}, "effectiveFrom": {"type": "string", "format": "date-time"}, "approvalReference": {"type": "string", "minLength": 1, "maxLength": 120}},
        },
        "ModelRateCreated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "revision"],
            "properties": {"id": {"type": "string", "format": "uuid"}, "revision": {"type": "integer", "minimum": 1}},
        },
        "CreateEmbeddingRevision": {
            "type": "object", "additionalProperties": False,
            "required": ["provider", "modelId", "dimensions", "chunkingRevision", "approvalReference"],
            "properties": {
                "provider": {"const": "BEDROCK"},
                "modelId": {"const": "amazon.titan-embed-text-v2:0"},
                "dimensions": {"const": 1024},
                "chunkingRevision": {"type": "string", "minLength": 1, "maxLength": 40},
                "approvalReference": {"type": "string", "minLength": 1, "maxLength": 120},
            },
        },
        "EmbeddingRevisionCreated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "enabled"],
            "properties": {"id": {"type": "string", "format": "uuid"}, "enabled": {"const": False}},
        },
        "ReindexKnowledge": {
            "type": "object", "additionalProperties": False,
            "required": ["sourceService", "embeddingRevisionId"],
            "properties": {
                "sourceService": {"enum": ["COMMERCE", "EXPERIENCE"]},
                "resourceId": {"type": ["string", "null"], "format": "uuid"},
                "embeddingRevisionId": {"type": "string", "format": "uuid"},
            },
        },
        "ReindexQueued": {
            "type": "object", "additionalProperties": False,
            "required": ["jobId", "state"],
            "properties": {"jobId": {"type": "string", "format": "uuid"}, "state": {"const": "QUEUED"}},
        },
        "ActivateModelPolicy": {
            "type": "object", "additionalProperties": False,
            "required": ["evaluationResultId", "reason"],
            "properties": {"evaluationResultId": {"type": "string", "format": "uuid"}, "reason": {"type": "string", "minLength": 1, "maxLength": 500}},
        },
        "ModelPolicyActivated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "revision", "approved"],
            "properties": {"id": {"type": "string", "format": "uuid"}, "revision": {"type": "integer", "minimum": 1}, "approved": {"const": True}},
        },
        "AiReport": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "requests",
                "escalations",
                "topIntents",
                "emptySearches",
                "tokenUsage",
                "costEstimate",
                "latencyMs",
            ],
            "properties": {
                "requests": {"type": "integer", "minimum": 0},
                "escalations": {"type": "integer", "minimum": 0},
                "topIntents": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "required": ["intent", "count"],
                        "properties": {
                            "intent": {"type": "string"},
                            "count": {"type": "integer"},
                        },
                    },
                },
                "emptySearches": {"type": "integer", "minimum": 0},
                "tokenUsage": {"type": "integer", "minimum": 0},
                "costEstimate": {"type": "string", "pattern": "^[0-9]+(\\.[0-9]+)?$"},
                "latencyMs": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["p50", "p95"],
                    "properties": {
                        "p50": {"type": "integer", "minimum": 0},
                        "p95": {"type": "integer", "minimum": 0},
                    },
                },
            },
        },
        "CreateConversation": {
            "type": "object", "additionalProperties": False,
            "required": ["market"],
            "properties": {
                "market": {"type": "string", "minLength": 1, "maxLength": 32, "pattern": "^[A-Z0-9_-]{1,32}$"},
                "locale": {"type": ["string", "null"], "maxLength": 16, "pattern": "^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,4}$"},
            },
        },
        "ConversationCreated": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "expiresAt", "anonymousToken"],
            "properties": {
                "id": {"type": "string", "format": "uuid"},
                "expiresAt": {"type": "string", "format": "date-time"},
                "anonymousToken": {"type": ["string", "null"], "maxLength": 512},
            },
        },
        "SendMessage": {
            "type": "object", "additionalProperties": False,
            "required": ["message", "clientRequestId"],
            "properties": {
                "message": {"type": "string", "minLength": 1, "maxLength": 2000},
                "clientRequestId": {"type": "string", "format": "uuid"},
            },
        },
        "SourceDTO": {
            "type": "object", "additionalProperties": False,
            "required": ["id", "url", "title", "revision"],
            "properties": {
                "id": {"type": "string", "format": "uuid"},
                "url": {"type": "string", "format": "uri", "pattern": "^https://"},
                "title": {"type": "string", "maxLength": 200},
                "revision": {"type": "integer", "minimum": 0},
            },
        },
        "AnswerDTO": {
            "type": "object", "additionalProperties": False,
            "required": ["status", "answer", "sources", "products", "handoffUrl", "runId"],
            "properties": {
                "status": {"enum": ["ANSWERED", "CLARIFY", "ESCALATED"]},
                "answer": {"type": "string"},
                "sources": {"type": "array", "items": {"$ref": "#/components/schemas/SourceDTO"}},
                "products": {"type": "array", "items": {"type": "object", "additionalProperties": False, "required": ["id", "reason"], "properties": {"id": {"type": "string", "format": "uuid"}, "reason": {"type": "string"}}}},
                "handoffUrl": {"type": ["string", "null"], "format": "uri", "pattern": "^https://"},
                "runId": {"type": "string", "format": "uuid"},
            },
        },
        "SubmitFeedback": {
            "type": "object", "additionalProperties": False,
            "required": ["rating"],
            "properties": {
                "rating": {"type": "integer", "enum": [-1, 1]},
                "category": {"type": ["string", "null"], "maxLength": 30, "pattern": "^[A-Za-z0-9_-]{1,30}$"},
                "comment": {"type": ["string", "null"], "maxLength": 1000},
            },
        },
        "FeedbackReceived": {"type": "object", "additionalProperties": False, "required": ["received"], "properties": {"received": {"type": "boolean"}}},
    }
    idem = {
        "name": "Idempotency-Key",
        "in": "header",
        "required": True,
        "schema": {"type": "string", "minLength": 1, "maxLength": 100},
    }
    json_body = lambda name: {
        "required": True,
        "content": {
            "application/json": {"schema": {"$ref": f"#/components/schemas/{name}"}}
        },
    }

    def response(code, name, description):
        return {
            code: {
                "description": description,
                "content": {
                    "application/json": {
                        "schema": {"$ref": f"#/components/schemas/{name}"}
                    }
                },
            }
        }

    paths = {
        "/api/v1/ai/conversations": {
            "post": {"operationId": "createAiConversation", "security": [{}, {"bearer": []}], "parameters": [idem], "requestBody": json_body("CreateConversation"), "responses": response("201", "ConversationCreated", "Conversation created with server-verified owner and configured expiry")}
        },
        "/api/v1/ai/conversations/{id}/messages": {
            "post": {
                "operationId": "sendAiConversationMessage", "security": [{"bearer": []}, {"conversationToken": []}],
                "parameters": [
                    {"name": "id", "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}},
                    {"name": "If-Match", "in": "header", "required": True, "schema": {"type": "string", "pattern": '^\\"[0-9]{1,19}\\"$'}},
                    {"name": "X-Conversation-Token", "in": "header", "required": False, "schema": {"type": "string", "maxLength": 512}, "description": "Signed expiring anonymous ownership capability; never an owner or party identifier."},
                ],
                "requestBody": json_body("SendMessage"),
                "responses": {"200": {"description": "Server-validated bounded SSE answer", "content": {"text/event-stream": {"schema": {"type": "string"}}}}, "202": {"description": "Provider outcome is uncertain; no blind retry", "content": {"application/json": {"schema": {"type": "object"}}}}},
            }
        },
        "/api/v1/ai/runs/{id}/feedback": {
            "post": {"operationId": "submitAiRunFeedback", "security": [{"bearer": []}, {"conversationToken": []}], "parameters": [{"name": "id", "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}}, {"name": "If-Match", "in": "header", "required": True, "schema": {"type": "string", "pattern": '^\\"[0-9]{1,19}\\"$'}}, idem, {"name": "X-Conversation-Token", "in": "header", "required": False, "schema": {"type": "string", "maxLength": 512}}], "requestBody": json_body("SubmitFeedback"), "responses": response("200", "FeedbackReceived", "Owner feedback recorded once with redacted comment")}
        },
        "/internal/v1/knowledge/reindex": {
            "post": {
                "operationId": "reindexPublicKnowledge",
                "security": [{"workload": ["ai.manage_WITH_SIGNED_ADMIN"], "signedActor": []}],
                "parameters": [idem],
                "requestBody": json_body("ReindexKnowledge"),
                "responses": response("202", "ReindexQueued", "AI-owned ingestion job queued after exact workload and signed actor authorization"),
            }
        },
        "/api/v1/staff/ai/embedding-revisions": {
            "post": {
                "operationId": "createAiEmbeddingRevision",
                "security": [{"bearer": []}],
                "parameters": [idem],
                "requestBody": json_body("CreateEmbeddingRevision"),
                "responses": response("201", "EmbeddingRevisionCreated", "Immutable 1024-dimensional embedding revision created disabled"),
            }
        },
        "/api/v1/staff/ai/model-policies": {
            "post": {"operationId": "createAiModelPolicy", "security": [{"bearer": []}], "parameters": [idem], "requestBody": json_body("CreateModelPolicy"), "responses": response("201", "PolicyCreated", "Immutable disabled policy revision created")}
        },
        "/api/v1/staff/ai/model-policies/{id}/activation": {
            "post": {"operationId": "activateAiModelPolicy", "security": [{"bearer": []}], "parameters": [{"name": "id", "in": "path", "required": True, "schema": {"type": "string", "format": "uuid"}}, {"name": "If-Match", "in": "header", "required": True, "schema": {"type": "string", "pattern": '^\\"[0-9]{1,19}\\"$'}}, idem], "requestBody": json_body("ActivateModelPolicy"), "responses": response("200", "ModelPolicyActivated", "Policy activated only after passing evaluation, budget and configured provider gates")}
        },
        "/api/v1/staff/ai/prompt-revisions": {
            "post": {"operationId": "createAiPromptRevision", "security": [{"bearer": []}], "parameters": [idem], "requestBody": json_body("CreatePromptRevision"), "responses": response("201", "PromptRevisionCreated", "Immutable sanitized prompt revision created")}
        },
        "/api/v1/staff/ai/model-rates": {
            "post": {"operationId": "createAiModelRate", "security": [{"bearer": []}], "parameters": [idem], "requestBody": json_body("CreateModelRate"), "responses": response("201", "ModelRateCreated", "Immutable approved rate revision created; approval reference is stored only as an audit hash")}
        },
        "/api/v1/staff/ai/evaluation-suites": {
            "post": {
                "operationId": "createEvaluationSuite",
                "security": [{"bearer": []}],
                "parameters": [idem],
                "requestBody": json_body("CreateSuite"),
                "responses": response(
                    "201", "SuiteCreated", "Immutable suite revision created"
                ),
            }
        },
        "/internal/v1/evaluations": {
            "post": {
                "operationId": "runEvaluation",
                "security": [
                    {"workload": ["ai.manage_WITH_SIGNED_ADMIN"], "signedActor": []}
                ],
                "parameters": [
                    {
                        "name": "If-Match",
                        "in": "header",
                        "required": True,
                        "schema": {
                            "type": "string",
                            "pattern": '^\\"[1-9][0-9]{0,8}\\"$',
                        },
                    },
                    idem,
                ],
                "requestBody": json_body("RunEvaluation"),
                "responses": response(
                    "202",
                    "EvaluationQueued",
                    "Evaluation accepted; failed gates never activate a policy",
                ),
            }
        },
        "/api/v1/staff/reports/ai": {
            "get": {
                "operationId": "readAiReport",
                "security": [{"bearer": []}],
                "parameters": [
                    {
                        "name": "from",
                        "in": "query",
                        "required": True,
                        "schema": {"type": "string", "format": "date-time"},
                    },
                    {
                        "name": "to",
                        "in": "query",
                        "required": True,
                        "schema": {"type": "string", "format": "date-time"},
                    },
                ],
                "responses": response(
                    "200",
                    "AiReport",
                    "Sanitized aggregate report; no prompts or private tool output",
                ),
            }
        },
    }
    return {
        "openapi": "3.1.0",
        "info": {"title": "Nigson AI assistant, knowledge, evaluation and gateway API", "version": "1.1.0"},
        "paths": paths,
        "components": {
            "schemas": schemas,
            "securitySchemes": {
                "bearer": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"},
                "workload": {
                    "type": "oauth2",
                    "flows": {
                        "clientCredentials": {
                            "tokenUrl": "https://configured-keycloak.invalid/protocol/openid-connect/token",
                            "scopes": {
                            "ai.manage_WITH_SIGNED_ADMIN": "API-146/147: nigson-commerce workload and signed ADMIN actor"
                            },
                        }
                    },
                },
                "signedActor": {
                    "type": "apiKey",
                    "in": "header",
                    "name": "X-Nigson-Actor",
                    "description": "ES256 nigson-actor+jwt assertion bound to exact method, path, raw body and correlation; ADMIN and recent MFA are required.",
                },
                "conversationToken": {"type": "apiKey", "in": "header", "name": "X-Conversation-Token", "description": "Short-lived signed anonymous ownership capability tied to one conversation."},
            },
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    target = ROOT / "contracts/openapi/ai.yaml"
    expected = json.dumps(contract(), indent=2) + "\n"
    if args.check:
        if not target.exists() or target.read_text() != expected:
            raise SystemExit("FAIL: reviewed AI OpenAPI drift")
        scopes = json.loads(
            (ROOT / "contracts/security/workload-scopes.json").read_text()
        )
        exact = [entry for entry in scopes if entry.get("api") in {"API-146", "API-147"}]
        expected_scopes = [
            {
                "api": "API-146",
                "method": "POST",
                "path": "/internal/v1/knowledge/reindex",
                "owner": "ai",
                "client": "nigson-commerce",
                "scope": "ai.manage_WITH_SIGNED_ADMIN",
                "phase": 5,
            },
            {
                "api": "API-147",
                "method": "POST",
                "path": "/internal/v1/evaluations",
                "owner": "ai",
                "client": "nigson-commerce",
                "scope": "ai.manage_WITH_SIGNED_ADMIN",
                "phase": 5,
            },
        ]
        if exact != expected_scopes:
            raise SystemExit("FAIL: AI workload scope drift")
    else:
        target.write_text(expected)


if __name__ == "__main__":
    main()
