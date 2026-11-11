# ADR-009: PandaGo TEST-only delivery quotes

## Context

The launch architecture selected FedEx as its initial carrier. The user later
explicitly selected PandaGo for cart delivery quotes, initially in TEST, and
requested that carrier identity survive checkout and fulfillment without
creating labels or inventing a shipper origin.

The current PandaGo adapter supports private quote and booking protocols, but
Commerce's booking, shipment-result and tracking lifecycle is not yet provider
neutral. A quote-only activation must not imply that end-to-end PandaGo
fulfillment is ready.

## Decision

- Add PandaGo as an explicitly selected, opt-in TEST quote provider; do not
  silently replace the default carrier or call a real provider during local
  development.
- Keep the required FedEx launch-carrier decision in ADR-008 unchanged until a
  separately approved launch decision changes it.
- Require the PandaGo account fingerprint, qualification reference and expiry,
  NG/NGN pricing-provider approvals, enabled private-service security and
  provider credentials before the PandaGo Compose overlay can run.
- Keep order checkout disabled in the quote-only overlay until durable
  provider-specific booking, uncertain-outcome reconciliation, no-label status
  propagation and tracking are implemented and verified.
- Do not fabricate a shipper address, label, dispatch state or provider
  qualification. Use destination coordinates supplied on the customer's saved
  address.

## Consequences

PandaGo quote support can be tested independently while the general feature
defaults remain disabled. Checkout and booking stay blocked; existing FedEx
launch requirements and all provider qualification gates remain in force.

## Status

Accepted user direction; implementation and activation remain in progress.
Genuine PandaGo TEST calls are blocked until the registered account key and
approval inputs are verified. No live or test provider request is authorized
by this ADR alone.

## Supersedes

This ADR supplements ADR-008 for the explicitly requested PandaGo TEST quote
pilot only. It does not supersede ADR-008's FedEx launch-carrier decision.
