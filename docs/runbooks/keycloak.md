# Keycloak identity deployment and recovery

IDP-001 owns the vendor identity database and deployment. Commerce remains the
owner of business permissions and PostgreSQL BFF sessions. Identity roles and
workload scopes do not grant commerce roles or organization access. SEC-001
must qualify BFF state/nonce/session encryption, recent staff MFA, signed actor
trust and per-operation authorization before release.

The optimized image pins Keycloak 26.8.0 by digest. Local `start-dev` remains in
the existing local Compose setup. The production image accepts only
`start --optimized` or the explicit one-off bootstrap command. It refuses
missing approved hostnames, TLS/CA/password inputs, unrestricted proxy trust,
bootstrap credentials in permanent tasks, and test credentials/hosts outside the
isolated test environment. It has a read-only root, a non-root user, and writable
identity-data/tmp volumes. Runtime credentials are Secrets Manager references;
the image and Terraform state contain no credential values.

[Production configuration](https://www.keycloak.org/server/configuration-production),
[optimized containers](https://www.keycloak.org/server/containers),
[PostgreSQL TLS](https://www.keycloak.org/server/db),
[proxy isolation](https://www.keycloak.org/server/reverseproxy) and
[cluster discovery](https://www.keycloak.org/server/caching) govern the settings.
`hostname-admin` alone cannot isolate administration. The module creates a
private admin ALB restricted to approved VPN CIDRs. The public ALB defaults to
403; its WAF permits only the configured realm, resources and well-known paths,
rejects encoded/traversal/ambiguous paths, and disables request sampling. It
normalizes paths before allowlisting, following
[AWS WAF transformations](https://docs.aws.amazon.com/waf/latest/developerguide/waf-rule-statement-transformation.html).
Management, master realm and admin routes are private. Do not add a public
management/admin listener or remove the WAF while enabling the service.

Two Fargate replicas use JDBC_PING discovery and Keycloak's automatic cluster
TLS over self-restricted ports 7800/57800. Persistent vendor user sessions live
in the dedicated PostgreSQL database. No business service role receives access
or identity table privileges. A dedicated Multi-AZ encrypted RDS instance,
forced database TLS, protected deletion/final snapshots and explicit approved
backup/log retention are configured. Public IP assignment and arbitrary task
egress are disabled. ALB health checks use private HTTPS management readiness;
the healthy-replica alarm goes to supplied operator SNS destinations. Raw HTTP
access/authentication event logs are disabled. Realm security/admin audit events
are persisted in the encrypted identity database with the operator-supplied
retention. User events use eventsExpiration; administrative events use the
separate adminEventsExpiration realm attribute, including the private master
realm. Admin request representations are disabled. See the pinned vendor
[event store implementation](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/events/jpa/JpaEventStoreProvider.java).

## Required environment inputs

Supply actual environment/VPC/subnets/cluster, restricted admin and proxy subnet
CIDRs, endpoint/metrics security groups, distinct approved public/private DNS,
realm, exact BFF HTTPS callback, public/private ACM certificates, task TLS cert
and private key, database CA, immutable optimized ECR image, region-qualified
PostgreSQL18 minor and instance class, KMS key, Secrets Manager references,
backup/log/audit retention, policy revision, named operator and alert destinations.
The Terraform mock tests use synthetic metadata; those values are not deployment
examples or approvals. No production tfvars, DNS, account IDs, domains, provider
accounts or retention approvals are supplied by this task.

## Bootstrap and provisioning

Keep `service_enabled=false` initially. Apply the module only through the
approved infrastructure workflow once real inputs are supplied. From an
approved private operational environment, use the locked operational Python
environment (`make identity-sync`) and set `APP_ENV`, `IDENTITY_ADMIN_DSN` and
`IDENTITY_OWNER_PASSWORD` through protected runtime injection. The DSN must target
the dedicated instance's postgres database with `sslmode=verify-full` and a
trusted CA; never place credentials in command arguments/history. Execute:

```bash
.build/identity-env/bin/python scripts/identity/database.py
```

This creates `nigson_identity` as a non-superuser, non-role/database-creator,
non-replication/non-bypass role and the `keycloak` database owned by it. Existing
owner/privilege drift fails closed. An existing password is not silently rotated.
The master RDS credential is for this one-off operation only and is not attached
to the permanent service task.

With both replicas stopped, execute the module's `bootstrap_task_definition_arn`
as a one-off private Fargate task using the identity security group and private
subnets. Its command is `bootstrap-admin user`; supplied temporary username and
password come from separate secret references and a separate execution role.
Check the task exit status. Never enable bootstrap env vars on the permanent
service or rerun a bootstrap task while nodes are running. Follow the vendor's
[temporary bootstrap account procedure](https://www.keycloak.org/server/bootstrap-admin-recovery).

Start the private identity service for provisioning under the release guard.
From the approved private network, prepare a mode0600 credential JSON file with
`username`, `password`, current `totp` when required, and `clientSecrets` mapping
all phase-enabled `nigson-bff`, `nigson-commerce`, `nigson-experience`,
`nigson-integration` (and `nigson-ai` only BP5) to actual supplied secrets. There
are no defaults. Use explicit inputs:

```bash
.build/identity-env/bin/python scripts/identity/provision.py \
  --environment "$IDENTITY_ENV" --realm "$IDENTITY_REALM" \
  --callback "$IDENTITY_BFF_CALLBACK" --phase "$IDENTITY_PRODUCT_PHASE" \
  --revision "$IDENTITY_POLICY_REVISION" \
  --audit-retention-seconds "$IDENTITY_AUDIT_RETENTION_SECONDS" \
  --admin-origin "$IDENTITY_PRIVATE_ADMIN_ORIGIN" --ca "$IDENTITY_ADMIN_CA_FILE" \
  --credentials "$IDENTITY_PRIVATE_CREDENTIAL_FILE"
```

The client policy uses confidential BFF authorization code with S256 PKCE, an
exact redirect, no implicit/password grants, and service-account-only workload
clients. Scope grants come from `contracts/security/workload-scopes.json`,
including the revision's launch messaging/carrier APIs. Subject and target
audience mappers are explicit. No role/user passwords or automatic production
staff assignments are in the realm document. Retail business onboarding remains
a domain task; public registration/reset are closed in this identity baseline.
Staff's `nigson-staff-identity` marker requires TOTP enrollment at authentication
and grants no business privilege. When assigning/removing that marker, terminate
the affected user's existing vendor sessions and enforce the commerce recent-MFA
policy; do not treat an older SSO/refresh session as staff step-up evidence.

Create and enroll the actually approved permanent private administrator with
TOTP through the private console. Verify that account's administration access.
Then provide its protected credentials/current TOTP to retirement:

```bash
.build/identity-env/bin/python scripts/identity/retire_bootstrap.py \
  --admin-origin "$IDENTITY_PRIVATE_ADMIN_ORIGIN" --ca "$IDENTITY_ADMIN_CA_FILE" \
  --credentials "$IDENTITY_PERMANENT_ADMIN_CREDENTIAL_FILE" \
  --bootstrap-username "$IDENTITY_TEMPORARY_ADMIN_USERNAME"
```

Retirement requires distinct users, successful permanent administration and an
existing OTP credential; it logs out and deletes the supplied temporary user.
Disable/delete the temporary secret after the check. Do not invent a permanent
operator identity or retain the bootstrap credential as a permanent account.

## Token trust

The shared Java wire validator and owned BP5 Python validator enforce exact
Keycloak realm issuer, same-origin production JWKS, expected audience, allowlisted
`azp` and matching optional `client_id`, required operation scopes, access-token
`typ=Bearer`, subject, issued-at/expiry/not-before and a maximum60s skew.
Only RS256/ES256 and bounded public `kid` values are accepted. ID/unsigned/HMAC
and malformed/duplicate JSON tokens are denied. Keycloak's
[subject mapper](https://github.com/keycloak/keycloak/blob/26.8.0/services/src/main/java/org/keycloak/protocol/oidc/mappers/SubMapper.java)
is explicitly attached; custom scope import must not omit access-token subject.
JWK responses are bounded to64KiB/10keys, private keys are refused, encryption
keys are not signature trust, requests time out, redirects are refused, refresh
is bounded to one attempt per5s, and the five-minute cache fails closed on expiry.
No query token or Cognito `token_use` grants are introduced. Application
foundation routes remain denied until SEC-001 implements the complete boundary.

## Backup, restore and upgrade

RDS scheduled backups/final snapshots use the approved retention and KMS key.
The service role has no RDS snapshot/restore or S3 archive permissions. The named
recovery operator's approved cloud role/storage/key access and actual regional
snapshot/PITR restore must be qualified under INFRA/OPS release gates. Never
reuse a business database or let a business runtime role become a recovery role.

For a consistent vendor logical snapshot, stop both replicas during the approved
maintenance window. Dump only `keycloak` using PostgreSQL18 `pg_dump -Fc
--no-owner`; output to a protected mode0600 file and use protected environment
credentials/verified TLS. Identity backup contains keys, MFA credentials and
sessions: never attach a raw dump or token fixture to an issue/report. The
streaming AES256-GCM envelope uses a separately supplied mode0600 32-byte key,
authenticates the policy revision/checksum, and refuses target overwrites:

```bash
.build/identity-env/bin/python scripts/identity/backup.py encrypt \
  --source "$IDENTITY_DUMP_FILE" --target "$IDENTITY_ENCRYPTED_BACKUP_FILE" \
  --key "$IDENTITY_BACKUP_KEY_FILE" --revision "$IDENTITY_POLICY_REVISION"
.build/identity-env/bin/python scripts/identity/backup.py decrypt \
  --source "$IDENTITY_ENCRYPTED_BACKUP_FILE" --target "$IDENTITY_RESTORED_DUMP_FILE" \
  --key "$IDENTITY_BACKUP_KEY_FILE" --revision "$IDENTITY_POLICY_REVISION"
```

Store the envelope and key in separately approved/access-controlled encrypted
storage with the actual retention policy. Restore into a fresh dedicated TLS
identity instance, run the owner bootstrap, then `pg_restore --no-owner
--exit-on-error` as the identity owner. Use the same pinned image first; verify
issuer/client policy, signing keys, staff MFA and surviving refresh sessions
before routing traffic. Erase temporary plaintext only under the approved
operator policy. Authentication/tag/checksum failure never publishes restored
plaintext. No mandatory recovery policy or live restore is marked passed merely
because a snapshot resource exists.

Perform same-version replica replacement one at a time, retaining at least one
healthy replica and allowing bounded health/cluster convergence. A same-image
replacement drill is not qualification for an arbitrary version upgrade.
Follow [Keycloak's upgrade guide](https://www.keycloak.org/docs/26.8.0/upgrading/).
Only documented compatible rolling patch changes may use rolling updates.
For minor/major or schema-incompatible upgrades, stop all nodes, take/validate a
snapshot, test the target image against a restored copy, and schedule the
maintenance window. Never downgrade a migrated database; rollback restores the
matching pre-upgrade database and pinned image. Actual target-version/AWS
qualification remains NOT_RUN until supplied and tested.

## Offline qualification

Build the production artifact locally, then run the locked isolated suite:

```bash
docker build -f infrastructure/docker/keycloak/Dockerfile -t nigson/keycloak:qualification .
make identity-check ENV=test UV=.build/uv-package/bin/uv
make identity-check ENV=test PROFILE=ai UV=.build/uv-package/bin/uv
make identity-infra-check
```

`PROFILE=ai` requires `make ai-sync` and adds BP5 wire tests; BP1 identity checks
use their own locked environment and do not resolve or start the AI service.
The harness uses generated test-only TLS/credentials, two optimized replicas,
verified PostgreSQL TLS, a test-only proxy, PKCE/browser/TOTP authentication,
private admin retirement, real signed tokens, replica failure/replacement and
fresh-database authenticated restore. It compares original `nigson-local`
container IDs/start times/mounts before/after and cleans only its own resources.
Private fixtures/logs stay ignored in `.build/`; committed evidence contains only
classified results and original infrastructure metadata. The test proxy is not
a production microservice or a substitute for actual AWS ALB/WAF qualification.

BP1 release composition still requires SEC-001, carrier/messaging/payment live
qualification and infrastructure/restore/security gates. Missing credentials,
domains, approvals and activation policy block genuine deployment/release only;
they do not trigger mock fallback or invented approval values.
