# AI knowledge operations

API-179 creates an immutable 1024-dimensional Bedrock Titan v2 embedding
revision with normalization enabled and `enabled=false`. Its approval reference
is stored only as an audit checksum. The API deliberately does not activate a
revision; complete rebuild plus retrieval evaluation is required before any
activation path can be added or used. The repository has no production embedding
approval, model credentials, budget, or active embedding revision.

API-146 uses the exact `nigson-commerce` workload scope
`ai.manage_WITH_SIGNED_ADMIN`, a private peer, and an ES256 signed ADMIN
assertion bound to method, path, raw body and correlation. It records an
idempotent AI-owned ingestion job and audit row, returning `QUEUED`. Repeated
active work for the same source and embedding revision shares the durable job.
The API does not accept arbitrary URLs or source text.

The ingestion service accepts only current, approved `PUBLIC` source snapshots
with a matching checksum, a supported owner/type pair, explicit locale and
market, and no detected direct personal identifiers. It chunks within the
existing 1200-token row bound, embeds outside database transactions, verifies
the complete contiguous chunk set, and commits the validated revision and
active pointer atomically only when the embedding revision is already enabled.
Disabled revisions can be fully built and remain `VALIDATED` without changing
the active pointer. Owner approval is checked again after embedding. If source
approval is withdrawn, the AI document pointer is cleared and active evidence is
marked withdrawn. Retrieval filters to active, non-withdrawn PUBLIC revisions,
combines bounded PostgreSQL full-text and cosine rankings, then requires a
current owner validation result for every candidate. Owner validation failure
fails closed.

`LocalKnowledgeProvider` supplies deterministic source snapshots and normalized
1024-dimensional vectors only in local/test. It is stateful and supports stable
request replay, known unavailability and acceptance-timeout reconciliation.
Production has no default source or embedding adapter in this increment; queued
jobs remain durable until deployment supplies configured owner HTTP adapters
and approved embedding execution. Do not use the test mock as a production
fallback.

The Commerce API-123 catalogue knowledge DTO omits locale. No default locale is
invented here; a catalogue adapter must obtain an approved explicit locale before
indexing. Experience content and each owner source must provide explicit current
revision, locale, market, checksum and public URL metadata. The current public
contracts do not provide an embedding activation API, so validated test or
production revisions stay inactive until that contract and evaluation gate are
implemented and approved. No retention period is defined by this runbook.
