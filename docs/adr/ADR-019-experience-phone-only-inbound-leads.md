# ADR-019: Phone-only verified inbound leads

Status: Accepted for MSG-001 offline implementation, 2026-10-16

## Context

API-190 creates a private CONTACT lead from a bounded, signed Twilio WhatsApp message. The Experience lead table historically required `email NOT NULL`, while API-190 provides only a verified phone number. Synthesizing an email would create false identity data; copying sender or message plaintext into the lead would defeat the inbound encrypted-record design. The API-190 response wording also required an empty TwiML response while its business rule required a safe HELP response.

## Decision

Make `experience.lead.email` nullable through additive migration 023. Existing public lead request DTOs and validators continue requiring a valid email where those APIs require one. API-190 creates a generic CONTACT record with no email, phone, party, or message plaintext; the signed inbound record stores the phone and bounded body as encrypted ciphertext and links to that lead. Staff context remains a separately authorized API. The migration rollback refuses while any null email exists.

For `HELP`, return a fixed static safe TwiML message after the owner transaction commits. Other dispositions return an empty TwiML `Response`. No outbound send is performed. This updates the earlier empty-only response wording to reconcile it with the explicit HELP business rule.

Ciphertext uses a purpose-separated key. An explicit `NIGSON_INBOUND_RETENTION_DAYS` (1–3650) controls message retention; a bounded scheduled owner task erases sender/body ciphertext and retains only the keyed sender hash, provider SID and disposition for durable deduplication. Rollback of the nullable sender ciphertext migration refuses while any row has been erased. No retention duration or live sender/account metadata is inferred. API-190 remains feature-off until real values and operational qualification are supplied.

## Consequences

Lead consumers must tolerate absent email for verified inbound contacts; the public DTO shape does not change. Existing email-based lead endpoints remain unchanged. The additive migration has a guarded rollback and can be applied after the BP1 schema set. Ciphertext and retention configuration are explicit; no retention duration or live sender/account metadata is inferred. API-190 remains feature-off until real values and operational qualification are supplied.
