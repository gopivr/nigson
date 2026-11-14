# ADR-027: Normal admin login for selling-price changes

## Context and authority

On 14 November 2026 the user explicitly requested that price changes work through normal admin login without OTP. This supersedes the earlier MFA requirement only for price-book management and retail market price-book assignment.

## Decision

A valid Commerce session whose actor has a current persisted ADMIN assignment may list, create, revise and activate price books (API-150/027/151/028), and assign the retail book (API-162), without recent MFA. Current active party/identity matching, non-organization context and non-revoked ADMIN assignment are checked before replay and again under the command transaction. Browser role claims alone do not grant authority. The alternative SALES/FINANCE workflow retains its existing role and MFA requirements.

The exception is implemented in pricing-specific authorization methods. General market administration, organization pricing assignments, payments, refunds, role administration and all other existing MFA requirements are unchanged. Validation, CSRF/session controls, version checks, idempotency, immutable books, audit and outbox events remain required. No credentials, role assignments or MFA timestamps are synthesized.

## Consequences

An active admin can complete the product editor retail-price workflow with normal login. Accounts without current ADMIN authority cannot use the exception. The updated Commerce runtime must be deployed/restarted for the changed policy to take effect.

## Status

Accepted explicit user decision; source and local checks are recorded in the implementation report. Live authenticated acceptance remains separate.
