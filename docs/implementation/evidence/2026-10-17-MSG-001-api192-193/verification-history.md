# Verification history

The following failed runs were retained during implementation and corrected before acceptance:

- The initial sandbox run could not reach the local Docker socket. The previously authorized native test workflow succeeded when run with local Docker access.
- The first native staff-reply attempt rejected a mock Twilio service SID because the fixture did not use a mock credential. The fixture now uses a dummy SID accepted only by local MOCK mode.
- Subsequent tests exposed template selection ambiguity when multiple active revisions reused the same provider Content SID. Integration now requires an explicitly configured active template revision and matches that exact revision and SID.
- A draft ciphertext assertion treated a random ciphertext as if any matching byte were plaintext, and worker tests could claim another test's pending job. The assertion now checks exact plaintext inequality and decrypts with the correct job/field AAD; fixture cleanup transitions its own synthetic jobs to terminal state through Integration-owned permissions.
- An attempted fixture cleanup across `integration.message_receipt` was rejected by owner permissions and was removed. Tests do not grant the runtime role extra access.
- Migration verification initially used a mistyped Java path, then had stale changeset comparison assumptions. The probe now compares both newly added Integration changesets against the prior accepted checksum history and passes empty rollback/reapply plus populated-context rollback refusal.

No failed result was counted as passing evidence. The final native and migration results are in `native-final.txt` and `schema-upgrade.json`.
