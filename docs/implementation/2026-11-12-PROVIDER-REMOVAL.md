# Provider removal
Status: DONE. Repository commit: `b08cf99`; follow-up shutdown/test changes remain uncommitted.

Retired carrier adapters, credentials, SDK-specific workers/tests/fixtures, deployment settings and frontend labels removed. PandaGo quotes remain. Automatic booking without an execution workflow now fails before creating intent; private unsupported operations return explicit unavailable errors. Applied migration files/records remain unchanged; additive `026-bp1-selected-carrier` restricts new quotes without rewriting history.

Validation: clean service build, all active frontend builds, contract/schema checks and offline provider qualification pass. Isolated database manual fulfillment and PandaGo quote/cart/account/pricing binding tests pass. Earlier failures exposed legacy fixture assumptions and an obsolete startup-origin requirement; both were corrected without weakening provider/pricing checks.

Evidence: `.build/provider-removal/{java-clean.log,contracts.log,native-fulfillment.log,native-quote-binding.log,offline-provider-qualification.log,quote-configuration.log,final-runtime-build.log}`. Packaged Integration artifact contains no retired adapter classes. Main changes: `services/integration/src/main/java/com/nigson/integration/{providers,carrier}`, Commerce fulfillment/pricing, `docker-compose.yml`, `.env.example`, contract generators and migration wrapper; UI carrier labels.

Runtime restart initially exposed the old origin requirement; regression test and final images include the fix. The user subsequently requested shutdown: `make local-down` succeeds with all named volumes preserved. Stack remains stopped; startup after the origin fix was intentionally not attempted. Genuine delivery booking remains disabled; no external shipment/payment/message was created.
