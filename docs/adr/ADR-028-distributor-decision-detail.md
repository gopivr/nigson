# ADR-028: Saved distributor decision reason in staff detail

## Context

The user requested a guided distributor review/approval page with a visible rejection reason. Experience already persists the decision reason in distributor_application.decision_payload, but API-177 exposed only the review note. Displaying that review note as a rejection reason would misrepresent the recorded decision.

## Decision

Add nullable decisionReason to the Sales-authorized DistributorApplicationDetail response, read from the existing Experience-owned decision_payload reason. Retain reviewReason separately. No mutation, schema migration, event, cross-owner SQL or authorization change is introduced. Existing clients may ignore the additive field; updated clients show unavailable text if an older runtime omits it.

The frontend preserves raw application states and reviewerPartyId, enforces the existing independent-approver rule before making a request, uses If-Match and human-entered notes, prevents repeated in-flight decisions, and refreshes after uncertain or rejected writes. Partner leads use their existing lead transitions rather than simulated distributor approval. Backend authorization/state validation remains authoritative.

## Limits

Only the latest saved decision and review are exposed; this is not a historical audit endpoint. Distributor proof downloads remain applicant-only under existing file access rules; this change does not grant Sales staff arbitrary document access. The page explicitly describes that limitation. No entitlement activation is claimed until Commerce acknowledgement.

## Status

Accepted implementation decision for the user-authorized workflow update. Local build/test evidence and remaining live/database qualification are recorded in the session report.
