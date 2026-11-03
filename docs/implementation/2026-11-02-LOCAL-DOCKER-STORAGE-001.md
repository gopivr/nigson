# LOCAL-DOCKER-STORAGE-001 — local Docker storage recovery

User reported `make run-core` failing with BuildKit `metadata_v2.db: input/output error`.
Host inspection found only 267 MiB free on the macOS Data volume. Docker image
listing also failed reading a containerd content blob, confirming an engine storage
failure before application build execution.

Removed three session-created temporary diagnostic JAR copies (about 330 MiB).
The first unused older build-cache prune failed with the same I/O error. Restarted
Docker Desktop under the user's existing explicit restart authorization. The user
then explicitly approved removing the two cached VS Code/Postman updater downloads
outside the repository. Removed only those two `update.*` directories; installed
applications, configuration, source and named database volumes were preserved.
The subsequent `docker builder prune -f --filter until=24h` succeeded, reclaiming
3.066 GB of unused build cache. `docker system df` became readable; macOS reported
6.4 GiB free. No image prune, volume prune or factory reset was performed.

Added a host free-space preflight before every local Compose build. Below 5 GiB,
it reports the measured available space and the safe recovery action before
invoking Docker. This checks host space, not Docker VM capacity or disk health.
22 local configuration tests passed, including rejection before invoking Docker
and sufficient-space acceptance. Full contract suite passed (107 tests).

Evidence: `.build/local-docker-storage-tests.log`,
`.build/bp2-bp5-contract-audit-final.log`.
Initial startup retry evidence: `.build/local-docker-recovery-run-core.log` (failure retained).
No commit created; unrelated staged work preserved.

The first startup retry passed compilation/tests but then exposed corrupted cached
Nigson Integration Maven `maven-metadata-local.xml` containing NUL bytes. All three
Java Dockerfiles now remove only generated `com/nigson/*/maven-metadata-local.xml`
under their locked cache mount before installing the reactor. Third-party pinned
downloads remain cached. Failure history is retained in
`.build/local-docker-recovery-run-core.log`; second retry evidence is
`.build/local-docker-recovery-run-core-final.log`.

LocalStack returned an empty KMS key list after engine recovery. Added and ran
`python3 scripts/local/restore_actor_key.py`, restricted to local `.env`/Compose
emulator calls. It first checks whether the configured key/public key matches,
otherwise creates an emulator P-256 signing key and updates only the actor trust
settings in ignored `.env`. No production KMS or session-key change occurs.

The second retry verified all migrations but the new preflight stopped the next
build at just under 5 GiB free. A further unused-cache prune limited to cache older
than one hour reclaimed 3.776 GB; asynchronous Docker discard raised host free
space to 8.5 GiB. Third startup log:
`.build/local-docker-recovery-run-core-complete.log`. Synthetic local document
objects were restored without resetting linked application records. Repeated
actor-key recovery verified the configured key exists and left it unchanged.

Final result: DONE locally. `make run-core` exited zero. All three application
containers reported healthy; Keycloak discovery and existing registration client
checks passed. Approximately 5.9 GiB remained after all images were rebuilt.
Final evidence: `.build/local-docker-recovery-run-core-complete.log`.
