# Provider mocks and qualification

Specification1.1.0 / ADR-008 / MOCK-001. Local setup starts three pinned WireMock
servers and Mailpit. Mocks have no published Compose ports. State lives in named
local volumes; administration is available only inside their containers. Genuine
credentials are never required for offline setup.

Use the pinned Oracle Java25 toolchain and existing Maven wrapper:

```sh
make local-up PROFILES=provider-mocks
make mock-reset ENV=local
make provider-scenario PROVIDER=fedex SCENARIO=booking-response-lost
make provider-scenario PROVIDER=twilio SCENARIO=duplicate-status
make provider-journal PROVIDER=all
make provider-contract-test PROVIDER=all MODE=mock
```

`success`, `decline`, `rate-limit`, `unavailable`, `timeout-after-acceptance`,
`booking-response-lost`, `duplicate-status` and `out-of-order-status` are bounded
registered scenarios. Progress/reset/state/callback controls are test-only. A
lost response occurs after persisted acceptance; verify/status/state inspection
reconciles it rather than resending. Refund and message sends do not pretend to
have native exactly-once guarantees. References, status transitions and operation
IDs persist across restart; reset is explicit. State and redacted journals are
bounded to128 operations/entries. Journals omit headers, bodies, recipient PII
and query strings. WireMock's built-in request journal/logging is disabled.

Paystack's commerce HTTP adapter initializes exact integer NGN kobo amounts,
verifies saved reference/amount/currency, creates partial refunds and queries
refunds. FedEx's private integration adapter obtains/caches OAuth tokens and
calls rates, booking, cancellation and tracking APIs. Integration's Twilio
adapter uses actual Messages form HTTP for SMS and approved WhatsApp ContentSid/
variables, with SID status queries. SMTP sends go to Mailpit in mock mode; genuine
SMTP requires authentication, STARTTLS and server identity verification. Provider
configuration is explicit in application beans. TEST/LIVE changes endpoint/mode/
credential configuration, rather than domain code. Production rejects mock
endpoints/keys and non-LIVE modes; redirects are refused. Mutating timeouts,
server failures and malformed successful responses remain uncertain.

The Testcontainers harness uses the actual provider adapters and production
signature verifier: raw-byte constant-time Paystack SHA512 and the official
Twilio RequestValidator with configured canonical URL and complete parameters.
A private test receiver records verification evidence for duplicate, out-of-order
and invalid signatures. It **does not** acknowledge a fictional financial or
messaging domain effect. Public application callback inbox/forwarding and once-only
financial/job state are PAYSTACK-001/MSG-001, which depend on security and domain
implementation. This resolves the supporting document's reference to real app
facades without adding a fake success endpoint to dependency-ready MOCK-001.
Those complete callback workflow checks must run against the app facades in the
corresponding tasks before BP1 release.

The harness denies external JVM HTTP before network access and allows loopback
HTTP/control sockets only. It verifies missing signatures and altered canonical
URL/account/SID/status fields using the production verifier.

The harness also verifies HTTP failures, accepted operations whose response is
lost, progression, restart persistence, bounded/redacted journals, reset, SMTP
and mode guards. Test ports bind127.0.0.1; callback traffic uses its private test
network. Production Docker images omit mock source/controllers/test dependencies.

Genuine qualification remains NOT_RUN until actual credentials/account/sender/
template/route context is supplied. No Nigerian FedEx service area, tax policy,
SMTP sender approval or Twilio onboarding is inferred from synthetic fixtures.
The synthetic FedEx JSON exercises documented field structure; CARRIER-001 must
capture the account-qualified official versioned schemas and normalized label/
reference reconciliation contract before release.

`make provider-live-check ENV=stage PROVIDER=paystack|fedex|twilio|all` is an
explicit, genuine **read-only** HTTP check. It requires the provider's mode/base
URL/credentials and an existing Paystack qualification reference+expected kobo,
an operator-supplied FedEx shipment-request file, or an existing Twilio message
SID. Missing/dummy input is refused before a provider call. No live check runs
automatically. Read-only success does not qualify financial settlement, route
eligibility, template/sender approval or the complete release workflow.
