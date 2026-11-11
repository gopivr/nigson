# Automatic delivery address lookup

Removed manual checkout coordinates. Integration resolves postal addresses with Google before requesting Pandago quotes, rejects imprecise matches, and returns specific lookup/configuration errors. Server configuration accepts GOOGLE_MAP_API_KEY from .env.

Verification: 18 targeted Java tests, 5 checkout tests, Angular shop build and Docker build passed. Authenticated postal-only checkout reached Google and returned GEOCODING_ACCESS_DENIED (correlation 4fefcbbe-95b4-4398-85c0-7d053bd8720b). Direct Google diagnostic: REQUEST_DENIED, billing must be enabled on the Google Cloud project. Automatic-address quote success remains unverified until billing is enabled. No booking/payment created; temporary address removed.

Evidence: .build/automatic-address-smoke.log, .build/automatic-address-targeted-fixed.log, .build/automatic-address-docker.log. Implementation present in backend commit 04302b6; this follow-up only records live verification. Next: enable Google project billing, then rerun python3 .build/pandago/automatic-address-smoke.py against the local stack.
