# ADR-008: Confirmed Nigeria launch providers and production Keycloak

## Context

On4 October2026 the user approved Nigeria/NGN, selected Paystack exclusively, required carrier API logistics with initial FedEx/mock, required SMS/WhatsApp with mock credentials and selected Keycloak even in production.

## Decision

Apply specification1.1.0 assumptions002/003/004/012/013. Add stateful local/test Paystack/FedEx/Twilio mocks and real HTTP adapters. Carrier/messaging required at BP1. Use production Keycloak with dedicated vendor DB and hardened identity deployment; retain Java BFF/JDBC sessions and four domain services. No application source is generated in this edit.

## Alternatives

Previous provisional payment selection, manual launch logistics, email-only automation and Cognito production were superseded. Twilio is the initial selected messaging reference provider; FedEx is initial carrier adapter, account/route capabilities verified on release.

## Consequences

Additional identity operational ownership and carrier/messaging schema/API/tests; real account/sender/template/routes and unapproved tax/returns/campaign/AI policies still require configuration. Mock tests cannot establish live deliverability/service eligibility. Credentials plus required account metadata can be replaced through config without source rewrite.

## Status

Accepted user clarification. Live provider/account qualification pending actual credentials/config.

## Supersedes

Conflicting identity/provider/activation recommendations in ADR-004/ADR-006 and master1.0.0 only; their service/transaction/isolation decisions remain. Architecture1.0 source is historical, not silently rewritten.
