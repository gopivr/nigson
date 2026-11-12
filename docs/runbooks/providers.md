# Provider test simulators and live qualification

Provider simulators are available only inside isolated automated tests. The
application Compose profile does not start business-provider mocks or route
application traffic to Mailpit; LocalStack and Mailpit remain infrastructure
utilities. Every non-test provider request uses the official live API.

Run the offline contract harness with the pinned Oracle Java25 toolchain and
existing Maven wrapper:

```sh
make provider-contract-test PROVIDER=all MODE=mock
```

The isolated harness covers registered success, decline, rate-limit,
unavailable, timeout-after-acceptance, booking-response-lost, duplicate-status,
out-of-order-status, and malformed Paystack verification scenarios. Scenario,
reset, state, and callback controls are confined to automated tests. A lost
response occurs after persisted acceptance;
verify/status/state inspection reconciles it rather than resending. Refund and
message sends do not pretend to have native exactly-once guarantees. State and
redacted test journals are bounded to128 operations/entries and omit headers,
bodies, recipient PII and query strings. WireMock's built-in request
journal/logging is disabled.

Paystack's commerce HTTP adapter initializes exact integer NGN kobo amounts,
verifies saved reference/amount/currency, creates partial refunds and queries
calls rates, booking, cancellation and tracking APIs. Integration's Twilio
adapter uses actual Messages form HTTP for SMS and approved WhatsApp ContentSid/
variables, with SID status queries. Automated SMTP capture is test-only; runtime
SMTP requires authentication, STARTTLS and server identity verification.
Provider configuration is explicit in application beans. Automated tests use
isolated simulators and make no provider sandbox/live calls; all non-test
application requests use LIVE mode. Provider sandbox calls are blocked. Mock
endpoints/keys and non-LIVE modes are rejected outside tests; redirects are
refused. Mutating timeouts, server failures and malformed successful responses
remain uncertain.

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
SMTP sender approval or Twilio onboarding is inferred from synthetic fixtures.
capture the account-qualified official versioned schemas and normalized label/
reference reconciliation contract before release.

explicit, genuine **read-only live API** check. It requires LIVE mode and
approved credentials plus an existing Paystack qualification reference+expected
message SID. Missing/dummy input is refused before a provider call. No live
check runs automatically. Read-only success does not qualify financial
settlement, route eligibility, template/sender approval or the complete release
workflow.

Paystack launch adapter and financial qualification: [offline and genuine qualification](paystack-qualification.md). `make paystack-check` adds native owned financial workflows and fail-closed release preflight to the provider HTTP regression. Genuine qualification remains independent of synthetic acceptance.
