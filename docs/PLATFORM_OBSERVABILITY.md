# PLAT-002 errors, audit and observability

Specification 1.1.0, sections 12/14/28/29, ADR-008. This foundation has no public
business API. Domain commands retain their roadmap tasks and authorization gates.
Commerce remains the sole transaction owner for payments/orders/stock/pricing/rewards.

All three Java boundaries and BP5 AI return the closed RFC9457 contract in
[problem.schema.json](../contracts/problem/problem.schema.json). Authentication
and permission failures use the same wire writer as validation and application
failures. Unknown exceptions produce static INTERNAL_ERROR details; SQL, exception
classes, provider text, claims and submitted values never become response details.
The type base is an approved configured URI; local/test uses a URN with no external
request. Instance identifies a normalized route with `_id`, never a raw protected
identifier or query. UUID correlation is validated or replaced; trace context is
validated independently. Ownership denial must use NOT_FOUND in future commands.

Java strict Jackson and Python StrictModel reject unknown DTO fields. The raw
request boundary caps bodies at 1 MiB, callbacks at 256 KiB. JSON structural
validation rejects duplicate keys, nesting above 32, objects above 100 keys and
arrays above 100. API-180's explicit 500-case exception applies only to its AI
route; other endpoint lower limits remain mandatory. Raw webhook bytes are kept
exactly intact and bypass structural JSON conversion until signature verification.
ApiValues and AI values supply UUID, decimal-string amount/positive quantity,
quoted version, idempotency, UTC, pagination, report-window and query guards.
Currency syntax is not commercial approval; domain metadata and SKU rules remain
mandatory. Multipart/file policy, rich-text sanitization and endpoint-specific
schemas are enforced in their owning file/content/domain tasks.

Owned AuditWriter requires an existing owner transaction. It stores UUIDv7,
verified actor subject/kind, typed action/resource/reason, occurrence/correlation
and allowlisted old/new values. Unknown field names and values are omitted with
a fixed redacted marker. Invalid allowed values abort the transaction. Reasons
are classified values; confidential free text is not stored. Optional IP hashing
uses HMAC-SHA256 over a parsed numeric IP with an explicit >=32-byte key; there is
no hostname resolution, raw-IP storage or default retention policy. Domain callers
must obtain the actor from verified identity and authorize before mutation. Audit
insertion and mutation commit or roll back together. Existing DATA-001 runtime
INSERT/SELECT-only audit privileges prohibit UPDATE/DELETE. Java persisted-audit
metrics increment after commit, never for rolled-back insertions. Privileged RDS
administrators remain an infrastructure trust boundary.

The sole instrumentation owner is each service's OpenTelemetry SDK. Do not attach
an agent or install a second FastAPI instrumentation boundary. Java instruments
HTTP, owned JDBC and actual provider HTTP; Python instruments ASGI, actual HTTPX
and psycopg connections. No global SDK is registered. HTTP children carry W3C
traceparent and X-Correlation-ID. Outbox persists the current HTTP envelope context
inside the command transaction. Producer and consumer spans start fresh traces
linked to that stored context, retain correlation, and restore thread/context state.
Consumers still commit before acknowledgement; no transaction crosses SNS/provider
HTTP. With no domain handler a message is not acknowledged.

JSON log encoders allow only the documented service/environment/version and
correlation/trace/span/code fields plus classified messages. They discard arbitrary
formatted arguments and throwables. They never export SQL text, binding values,
raw URLs, forms, prompts, tokens or provider messages. Python strips library span
attributes/events/status descriptions before SDK batching. Java instrumentation
creates only bounded approved attributes and no exception events. Metric names
and labels are registered closed values; normalized unknown routes become UNKNOWN.
Java bounds registry size to 2048; SDK queues hold 2048 spans, batches 256, span
attributes 16 (128 characters), events zero and links eight. Python SDK metrics
use its bounded cardinality policy. Query operations are SELECT/INSERT/UPDATE/
DELETE/OTHER; no UUID, email, SKU, provider SID or arbitrary path labels exist.

Owned Java outbox gauges query actual rows outside command transactions; missing
snapshots produce NaN, not healthy zero. Pool active/waiting/utilization and actual
acquisition timeout counts use Hikari. AI observes actual API/worker pool stats;
acquisition errors retain their truthful name. Native SNS/SQS supplies age/backlog/
inflight/DLQ metrics. Idempotency and publication counters retain real observations.
Domain-specific payment, stock, refund, messaging, identity and AI producers must
be added alongside those business tasks; this foundation fabricates none.

Telemetry export is explicitly disabled by default offline. Java supports
NIGSON_OTEL_ENDPOINT for traces, and NIGSON_METRICS_EXPORT_ENABLED=true plus
NIGSON_OTLP_METRICS_URL ending `/v1/metrics` for metrics. Python uses the configured
OTel endpoint for both. Local/test targets permit HTTP loopback or otel-collector
only. Other environments require NIGSON_TRACE_POLICY_ID and HTTPS or the explicit
127.0.0.1:4318 Fargate ADOT sidecar. Missing/invalid enabled settings fail startup.
NIGSON_PROBLEM_TYPE_BASE must be approved HTTPS outside local/test. Configure
application env directly; the ordinary Compose setup deliberately enables no
external collector or AWS exporter. No agent/collector fallback is automatic.

[The observability Terraform module](../infrastructure/terraform/modules/observability/main.tf)
requires an actual region from its caller/provider, KMS key, approved retention and
policy revision, named operator and alert SNS destinations. It creates encrypted
log groups, an operations dashboard, 5xx >1% for five minutes, outbox >60s for five
minutes, queue age >120s for five minutes, any DLQ message, pool >80% for ten
minutes, UNKNOWN payment >15m, either-sign stock difference, refund >30m and BP5
AI 80%/100% budget alarms. Missing telemetry remains INSUFFICIENT_DATA. AI resources
are absent before BP5; there is no invented activation/contact or notification.
Alarm notification is not a budget enforcement mechanism: AI-GATEW-001 must enforce
the actual cutoff transactionally. Business/identity/provider dashboards and their
policy-dependent alarm thresholds grow with the corresponding tasks.

The rendered ADOT configuration uses loopback OTLP/HTTP, a 256 MiB memory limiter,
10,000 retained trace cap, bounded batches and supported finite AWS request retry/
timeout settings. Tail sampling retains complete error/security-denial/checkout
traces and samples 10% baseline. The SDK explicitly records all safe spans, even when a remote caller sends an
unsampled traceparent; this preserves error/security/checkout evidence for tail
sampling. Sampling happens at the collector, after all safe SDK spans are transmitted; filtering individual spans would lose needed child
spans. The sidecar's IAM policy permits metric log-stream writes only to its
created group and X-Ray ingestion; it cannot create arbitrary log groups. INFRA-001
must attach it to the actual ECS role and qualify cloud delivery. The pinned ADOT
v0.50.0 ARM64 image is specified by collector_check.py and its actual digest.

Offline checks: make observability-check (fresh PostgreSQL/SNS/SQS and all owner
roles), make observability-infra-check (Terraform mock provider),
make collector-config-check (non-root/read-only/network-disabled ADOT startup),
make verify PROFILE=ai, make provider-contract-test and make cold-start.
The original nigson-local stack is preserved. Genuine CloudWatch/X-Ray/IAM delivery,
RDS/AMD64/ECS operation, live provider qualification and approved retention/export
storage are NOT_RUN until actual activation inputs exist. Scheduled encrypted
access-separated audit export/retention/legal holds and protected staff audit query
APIs remain COM-AUDIT-001/EXP-AUDIT-001/INFRA-001. There is no fake export or public
staff-query implementation in this foundation.

SDK and collector configuration references: [Java SDK](https://opentelemetry.io/docs/languages/java/sdk/),
[Python instrumentation](https://opentelemetry.io/docs/languages/python/instrumentation/),
[ADOT release](https://github.com/aws-observability/aws-otel-collector/releases/tag/v0.50.0),
[pinned X-Ray exporter settings](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.158.0/exporter/awsxrayexporter/README.md),
[CloudWatch EMF exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.158.0/exporter/awsemfexporter/README.md).

Commerce registers nigson.notification.outcomes with state SENT/FAILED/SUPPRESSED/UNKNOWN after immutable receipt commit, and nigson.notification.templates plus nigson.provider.commands with outcome ACKNOWLEDGED/UNKNOWN. nigson.files.transfer permits CLEAN/REJECTED/UNKNOWN. MetricPolicy rejects other state/outcome values and arbitrary names or labels; no recipient, job UUID or provider reference labels. Assembled notification acceptance verifies these counters survive the actual production filter.

Inventory records nigson.inventory.movements with the closed movement_type ADJUST/RESERVE/SETTLE/RELEASE/DISPATCH/RETURN only after the owning transaction commits. Replays and rollbacks increment no movement counter. Assembled BFF acceptance checks the production filter retains the counter; UUID labels are denied. Stock movement conservation and negative-availability rejection remain mandatory native checks, with staging dashboards/alert routing qualification tracked separately.
