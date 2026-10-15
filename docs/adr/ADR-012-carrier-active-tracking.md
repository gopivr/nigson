# ADR-012: Active carrier tracking and durable scan acknowledgement

Status: Accepted technical implementation; native132/no skips PASS. Full163 Java/289 gated,70 contracts,15 AI/five gated, stateful provider checks and nine reproduced artifacts PASS; accepted source 998893156dc4364edafc52dff96410345c7c391b.

The completed integration LOGISTICS_CREATE cursor additionally retains sorted unique physical-piece tracking identifiers. Existing completed jobs remain SUCCEEDED; active tracking leases the booking and fences both versions without resetting creation permission or its original acknowledgement. A small optional tracking checkpoint in the same owner-local opaque cursor persists the original source command before HTTP. No schema, owner boundary, public DTO or event changes.

The worker reads saved numbers in batches of at most30. It requires complete exact physical-piece coverage, rejects missing/duplicate/foreign/error results and uses provider scan timestamps rather than polling time. Cancellation/delivery is aggregate only when every piece agrees; mixed cancelled/active pieces enter an exception. No result may fabricate physical handover or change financial truth. Source API-120 remains authoritative, including its delivery-before-handover rejection. A pending source command is retried byte-for-byte without another provider read. Older/duplicate aggregate observations do not add commands. Terminal polling stops only after source acknowledgement, with delivery requiring a delivered source result.

Legacy complete records lacking piece identities cannot safely infer package coverage; they remain visible for reconciliation and never recreate. Account/mode mismatch cannot poll. Poll interval15 minutes; read failures preserve the booked intent and retry later, with safe errors/audit/metrics. No DB lock is held during provider or source HTTP.

Exact official TrackingNumbers-Resource.json/API1.0.0 SHA256 and interpreted field paths are captured in contracts/providers/fedex/track-profile-2026-10-15.json. The raw upstream schema was inspected, not described as genuine account execution.

Primary wire reference: https://developer.fedex.com/api/en-us/catalog/track/v1/docs.html (reviewed15 October2026). Native and genuine-account qualification remain separate; approved account routes and real scan/code coverage remain release inputs.
