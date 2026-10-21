# Tested dependency and toolchain lock

Specification 1.1.0 / FOUND-002, verified 5 October 2026 on macOS ARM64.

| Tool or dependency | Pin / source |
|---|---|
| Java compiler/runtime | Oracle JDK 25.0.2+10-LTS-69, Oracle Corporation |
| Maven | 3.10.0, official only-script wrapper 3.3.4 |
| Maven ZIP SHA256 | `1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff` |
| Maven ZIP SHA512 | `22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337` |
| Spring Boot parent/BOM/plugins | 4.1.1 |
| Spring Modulith BOM | 2.1.1 |
| Maven Enforcer | 3.6.2 |
| Maven Dependency plugin | 3.8.1 |
| Commons Text (outside Boot BOM) | 1.15.0 |
| AWS Java SDK BOM / SNS / SQS | 2.55.10 |
| Official Twilio signature SDK | 13.0.2 |
| Commons IO convergence pin | 2.22.0 |
| Testcontainers (test scope only) | 2.0.5 |
| WireMock standalone/image (test-only) | 3.13.2; hashes in docs/dependencies/mock-lock.json |
| Python | CPython 3.12.12, uv-managed python-build-standalone distribution |
| uv | 0.9.3 |
| FastAPI / Pydantic / Settings | 0.142.2 / 2.13.5 / 2.15.0 |
| LangGraph / boto3 | 1.2.12 / 1.43.108 |
| psycopg (binary/pool) / httpx | 3.3.6 / 0.28.1 |
| uvicorn | 0.54.0 |
| pytest / Ruff / mypy | 9.1.1 / 0.16.10 / 2.4.0 |
| Hatchling | 1.32.4, included in the locked dev group for offline wheel builds |

All other Java versions, including Jupiter, are selected by the Boot/Modulith BOMs.
No forced JUnit 5 or Spring/Hibernate overrides. Enforcer rejects Java 17, other
Java patches/vendors, other Maven versions, snapshots and dependency divergence.
The required JDK is already installed in this workspace's environment. Other
platforms must supply the same distribution/version; Windows wrapper is included
but was not executed. No automatic global JDK replacement is performed.

The first convergence check failed because Liquibase 5.0.3 selected Commons Text
1.15.0 while OpenCSV selected 1.13.1. Commons Text is not managed by Boot;
dependencyManagement now converges both paths on Liquibase's selected 1.15.0.
No convergence exclusion or disabled rule hides the conflict.

## Compatible security patches

The initial exact Boot baseline compiled, but the OSV scan reported ten advisories
across Tomcat 11.0.24 and Jackson 3.1.5. The final build keeps the Boot 4.1.1 and
Modulith 2.1.1 BOMs and applies same-line security properties: Tomcat 11.0.26,
Jackson 3 BOM 3.1.7 and Jackson 2 BOM 2.21.7. These are explicitly recorded
compatible patch overrides, not untested Spring/JUnit major overrides. Tomcat's
maintainer lists additional fixes in 11.0.26, so 11.0.25 alone was not selected.
[Tomcat security notes](https://tomcat.apache.org/security-11.html).

Jackson's parser and forward-reference issues include fixes in 3.1.7 / 2.21.7.
[Jackson Core advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf),
[Jackson Databind advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24).
BuildCompatibilityTest exercises the Boot Tomcat factory and Jackson mapper
together through real loopback HTTP, as well as Java/Boot/Modulith class linkage.
These six passing tests establish foundation compatibility only; full application
security, authentication and domain contracts belong to subsequent tasks.

Final OSV query: 161 resolved release artifact coordinates, zero known matches.
Python pip-audit 2.9.0: zero known matches on the complete exported runtime graph.
Advisory databases may lag and these results are timestamped evidence, not a
guarantee that the future deployed application has no vulnerabilities.

## Reproducible build commands

Set JAVA_HOME to the pinned JDK. Use `make verify` for Java and dependency-lock
verification. `MAVEN_BIN` can select another installed Maven 3.10.0+ if needed.
`make verify PROFILE=ai` adds Python; `UV` may specify an installed
uv 0.9.3 path. Core builds never resolve Python or call model providers.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
make verify
make verify PROFILE=ai UV=.build/uv-package/bin/uv
```

The Maven wrapper validates the downloaded ZIP SHA256. The Maven 3.10.0 ZIP's
SHA256 was computed from the archive and its SHA512 matched Apache's published
checksum. `.mvn/maven.config` puts dependency cache in `.build/m2`, and Make puts
wrapper/uv/interpreter/environments in `.build/`.
Do not remove checksum validation to work around a download failure.

After `dependency:tree -DoutputType=json -DoutputFile=target/dependencies.json`,
`python3 scripts/java_dependency_lock.py --check` compares every module graph
and resolved artifact SHA256 to `docs/dependencies/java-lock.json`. Updating a
dependency requires explicitly regenerating/reviewing that file after verification.
Build/plugin versions remain pinned by the parent POM and explicit properties.

Python uses `uv sync --locked`, exact direct versions and hash-locked transitives.
Build backend is installed from the same lock and `uv build --no-build-isolation`
uses that environment. Two independent clean virtual environments matched all
69 installed package/version pairs. Both produced identical wheel and sdist
hashes. Two final `clean verify` runs produced identical dependency/artifact
graphs and all seven JAR hashes. Output timestamp is fixed; results appear in
`docs/dependencies/build-artifacts.json`.

Python compatibility tests cover strict Pydantic rejection through actual FastAPI
HTTP, typed LangGraph execution, and loading required Bedrock SDK models without
credentials/inference/network. The Python namespace is a BP5 dependency foundation;
no AI assistant behavior is implemented or claimed complete.

## Remaining platform qualifications

PostgreSQL 18.6 / pgvector 0.8.2 are specification candidates, not tested image
pins in this task. FOUND-003 must verify local image availability and pin digests;
INFRA-001/AI-KNOWL-001 must qualify the selected AWS region/RDS extension and BP5
vector behavior. No AWS region was supplied or inferred. Container builds,
database migration/role tests and provider qualifications have not run in FOUND-002.

## PLAT-001 / MOCK-001 additions

AWS SDK2.55.10 is pinned by its BOM. The official Twilio13.0.2 RequestValidator
is used for complete webhook verification. Twilio requested Commons IO2.14.0,
which conflicted with the existing Liquibase5.0.3 dependency on2.22.0; Enforcer
rejected that graph. The explicit2.22.0 convergence pin retains the existing
qualified Liquibase version and passes the adapter/signature/migration checks.
Testcontainers2.0.5 is test scope only. WireMock3.13.2's standalone artifact
checksum and multi-platform Docker digest, plus auxiliary reaper/probe images,
are pinned separately. Application JARs contain neither mock controllers nor
Testcontainers.

Maven class/test output now uses `.build/java/<artifact>/` so concurrent IDE
incremental compilation cannot replace qualified classes with error stubs.
JARs and dependency graphs remain under each module's `target/`. Docker builds
install the reactor before copying runtime dependencies and include `jdk.net`
in the linked runtime because the qualified AWS Apache5 HTTP client needs it.
Primary references: [AWS SDK Java API](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/sns/package-summary.html),
[Twilio SDK](https://github.com/twilio/twilio-java),
[WireMock response extensions](https://wiremock.org/docs/extensibility/transforming-responses/),
[Testcontainers](https://java.testcontainers.org/).

## PLAT-002 observability additions

Java OpenTelemetry API/SDK/OTLP/testing 1.62.0 and Micrometer OTLP 1.17.1 remain
selected by the Boot BOM. Python SDK and OTLP HTTP exporter are pinned 1.45.1;
HTTPX/psycopg instrumentation 0.66b1 and their transitive hashes are in uv.lock.
The SDK owns instrumentation; no Java agent, global provider registration or
second ASGI auto-instrumentor is installed. Shared packages contain protocol/value
utilities, with all SDKs, repository writes and transactions in the owners.

ADOT v0.50.0 (contrib/core 0.158.0) is pinned for collector qualification to
`sha256:7968fb60db6a2390a47ba6a2df029745638486e285c9b2487da1b722d0855a3e`.
Actual ARM64 network-disabled startup verifies the rendered configuration;
CloudWatch/X-Ray/IAM delivery and AMD64 deployment remain NOT_RUN. The Terraform
AWS provider remains 6.15.0. PLAT-002 refreshed package-version advisory checks
(Java OSV and Python pip-audit) and retained their timestamped evidence separately.
See [the operational contract](PLATFORM_OBSERVABILITY.md) and the session report.

The Makefile now explicitly selects services/ai/pyproject.toml for strict mypy
when invoked from the monorepo root; the previously implicit invocation did not
load that nested configuration. This session's strict result uses the explicit
configuration and does not retroactively strengthen earlier evidence.

## IDP-001 identity additions

Production Keycloak uses the existing official26.8.0 manifest digest, an optimized
PostgreSQL/health/metrics/event-metrics build, and non-root read-only runtime. The
identity qualification PostgreSQL18.6 digest is the existing FOUND-003 pin. The
test-only TLS proxy pins nginx1.30.5-alpine at
`sha256:0985e772fb9f729e6fa0980da05fca5d9c468e870eed43071545afa9d2e27d94`;
it is not shipped as an additional production service. Terraform retains the
AWS6.15.0 provider lock. Optimized Fargate ARM64 is the module target; genuine
region/RDS/ACM/DNS/ALB/WAF/upgrade qualification is separately NOT_RUN.

BP1 private identity operational scripts use their own uv0.9.3/Python3.12.12
lock at `scripts/identity/uv.lock` with cryptography50.0.2 and
psycopg-binary3.3.6. They do not resolve/start the BP5 AI service. The optional BP5
owned JWT adapter adds PyJWT[crypto]2.15.1 and its locked cryptography/cffi
transitives. Public PyPI metadata and actual locked installs establish those
versions; no provider/model credentials are needed. The Java JWT adapter reuses
Boot-managed Spring Security/Nimbus/Jackson artifacts: Java public artifact
hashes are unchanged; the reviewed lock graph now includes the shared wire
adapter's explicit Spring dependencies. The operational Docker image pins the
same uv/Python base manifests as the existing AI foundation and excludes test
fixtures/harnesses.

`make identity-sync`, `make identity-policy-check`, `make identity-check` and
`make identity-infra-check` are independent of AI; `PROFILE=ai identity-check`
adds real Keycloak checks through the owned BP5 adapter after `make ai-sync`.
See [identity deployment/recovery](runbooks/keycloak.md) for required actual
inputs and exact commands. October9 public OSV and hash-locked pip-audit checks
are timestamped evidence, not future security guarantees.

## SEC-001 security additions

Commerce adds AWS KMS SDK2.55.10 from the existing pinned AWS BOM. Shared wire filters use existing Spring Security7.1.1 and the Boot-managed Jakarta Servlet API6.1.0 (provided); owner entities and JDBC session logic remain commerce-owned. No new production domain service or Python dependency is introduced. Terraform private trust retains AWS6.15.0 and uses the installed repository-compatible Terraform1.12.2. Exact graph/hash refresh and advisory results are recorded in the SEC-001 report after execution.


COM-PARTY-001 adds com.google.re2j:re2j:1.8 for bounded configured postal patterns, preserving the existing BOM selections. The graph locks 261 release artifact hashes. All 261 public Maven coordinates were queried against OSV with zero findings on 9 October 2026; see docs/implementation/evidence/2026-10-09-COM-PARTY-001/java-osv-audit.json. RE2/J upstream release/source: https://github.com/google/re2j/releases and https://github.com/google/re2j. COM-SESSI-001 changes no dependency coordinate; its Java and Python build hashes are regenerated for the implemented session/audit behavior.

COM-PLATF-001 configuration API-135 changes the registered safe-error vocabulary and owner service code without changing261 locked dependency artifacts. Its exact final build/reproducibility evidence and updated nine-artifact manifest are recorded in docs/implementation/2026-10-10-COM-PLATF-001.md.

COM-FILES-001 adds S3 and explicit Apache HTTP client from the existing AWS SDK2.55.10 BOM. The refreshed graph locks268 release artifact hashes; all268 public Maven coordinates returned zero OSV findings on10 October2026. Local storage uses an isolated TLS/SigV4 stateful fixture with bound dummy credentials; outside local/test the adapter uses ECS container credentials and approved regional KMS/bucket inputs. No Python production dependency changed. Exact test/build/advisory evidence will be recorded in docs/implementation/2026-10-10-COM-FILES-001.md after acceptance.
