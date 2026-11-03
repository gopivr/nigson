# ADR-023: Application-owned local account registration

- Status: Accepted for local/test UX by the user's explicit request on 1 November 2026.
- Decision owners: Commerce BFF and Nigson web application.
- Related decisions: ADR-004 (Java BFF OIDC), ADR-008 (Keycloak identity).

## Context

The MFE's signup route previously redirected users to Keycloak's hosted registration UI. The user requested account registration inside the Nigson application. Keycloak remains the identity provider; browser password grants and browser calls to the Keycloak Admin API are prohibited. Compose also used the public `localhost` issuer for Commerce's server-to-server token/JWKS calls, which cannot reach Keycloak from inside the Commerce container.

## Decision

Add API-207 `POST /bff/register` and a Nigson-owned signup form. The browser sends its registration request only to Commerce. Commerce sends the validated user/password to Keycloak through a separate `nigson-registration` confidential service account; Commerce does not store the password, issue tokens, assign application roles, or disclose whether an email already exists. New identities are created unverified. The UI then uses the existing authorization-code/PKCE sign-in flow.

For local/test only, the service account has the Keycloak `realm-management/manage-users` client role and Commerce limits requests to five per socket address per minute. The implementation refuses nonlocal `APP_ENV`; production registration remains disabled until reviewed fine-grained create-user permission, email verification, and distributed abuse controls are supplied. This local permission is not a production role recommendation.

Separate the public OIDC issuer used in token validation and browser redirects from the internal JWKS/backchannel endpoint used by Commerce for code exchange, refresh, logout, signing keys, and local registration. Issuer validation remains exact against Keycloak's externally configured issuer.

## Consequences

- The commerce OpenAPI contract adds API-207 and the registration request schema.
- Local Compose config defaults to `http://localhost:4200/bff/callback`, matching the documented MFE origin; deployments using another browser origin must set the callback and exact Keycloak redirect URI together.
- `make local-up` and `make run-core` idempotently configure only the named local registration client and its one role; they preserve existing Keycloak users and data.
- Duplicate emails receive the same 202 response as newly created accounts; malformed/password-policy rejection and provider unavailability remain safe errors.
- The local application does not yet send email verification or qualify production public signup.
