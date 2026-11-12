# ADR-026: Live APIs in application runtimes

Status: Accepted  
Decision date: 11 November 2026

## Context

The local Compose application previously routed Paystack, FedEx, Twilio, and
email traffic to stateful mock services. Application code also allowed local
mock adapters for provider and AI-backed features. This obscured whether
application requests reached the selected provider APIs.

## Decision

- Every application runtime profile (`local`, `dev`, `qa`, `stage`, and `prod`)
  uses official live provider endpoints and real, externally supplied account
  credentials.
- Provider mock modes and synthetic provider credentials are permitted only in
  the isolated automated `test` profile.
- LocalStack and Mailpit remain local infrastructure emulators; the application
  does not route business-provider requests to them.
- Missing live credentials/configuration fails startup or keeps the dependent
  feature closed. There is no mock fallback.
- Existing feature approvals, recipient/sender approvals, route restrictions,
  payment `UNKNOWN` handling, and release gates remain in force. This decision
  does not enable side-effecting features or authorize live qualification calls.

## Consequences

- Starting the local application requires real provider credentials and account
  configuration. `.env.example` contains official API hosts and blank secrets.
- Automated tests continue to use isolated HTTP simulators and synthetic
  fixtures; no ordinary test sends requests to a paid provider.
- Live provider qualification is a separate, explicitly approved release
  activity. It was not performed as part of this decision.
- Local developer workflows that lack provider configuration fail closed rather
  than exercise fabricated provider behavior.

This decision supersedes ADR-009's PandaGo TEST-endpoint allowance for
application runtimes. The PandaGo quote feature remains disabled by default and
requires explicit LIVE account approval and configuration before use.

## User-directed local exception (11 November 2026)
Local now uses real Paystack TEST credentials and official PandaGo stage APIs; production still requires LIVE. Runtime mocks remain forbidden. Local dummy Termii/SMTP credentials fail explicitly on use before external traffic. PandaGo replaces FedEx in the local application configuration.
