# ADR-022: Experience-owned file transfer intent and pinned scan bytes

Status: Accepted implementation decision within the Experience boundary; no live scanner or storage activation approval.

Context: Experience.file_asset predates the file-upload API implementation and does not persist creator-bound source metadata version/ETag, EVT-025 scan command binding, promotion lease or uncertain copy state. Completion and promotion must verify bytes outside database transactions; API-136 must not convert an uncertain copy into a blind retry or public clean asset.

Decision: Add BP1 Experience-owned `experience.file_transfer`, keyed by and restricted to its Experience `file_asset`. Persist owner, immutable quarantine and deterministic final keys, upload expiry, verified source version/ETag, EVT-025 event ID, callback request hash/result/scanner version, and fenced promotion lease/state. Transition UPLOADING -> QUARANTINED -> COPYING -> CLEAN or UNKNOWN; QUARANTINED -> REJECTED. A timeout or expired lease remains UNKNOWN; HEAD the immutable final object before a further decision, never repeat copy solely due to timeout. Callback command ID must match the EVT-025 ID and exact Integration workload scope/client. CLEAN remains unavailable until promotion is authoritatively verified. Career upload sessions remain their separate anonymous CAREER_CV capability and are not accepted by these APIs.

Storage and scanner implementations use typed ports. Local/test mode may use stateful mocks. Missing production bucket/KMS/scanner configuration fails closed; no real credentials or retention/activation defaults are created. Rejected/orphan retention and genuine provider qualification remain separate release inputs.

Consequences: Existing DATA-001 `experience.file_asset` DDL and its checksums remain unchanged. The additive Experience migration refuses populated rollback. Experience runtime owns only its schema; no cross-owner SQL/FK is introduced. Native migration validation must include clean/repeat behavior, owner-role isolation and populated rollback refusal.
