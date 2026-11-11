# Local sandbox delivery quotes

User approved real PandaGo sandbox quotes through 2026-11-17T23:59:59Z and synthetic measurements only for local demo electronics. Persisted overlay selection in run-core, configured the verified account, and seeded missing packaging on 24 demo SKUs with audit. Booking/order payment remain disabled.

Checkout accepts and validates delivery coordinates, persists them on new/saved addresses with real optimistic versions, and identifies FEATURE_DISABLED explicitly. Integration now uses its configured internal JWKS URL while preserving public issuer validation.

Verification: 23 local configuration tests and 5 checkout tests pass; Angular shop development build passes. Docker Integration rebuild (full Maven install) passed and the container was restarted healthy. Authenticated BFF/Commerce/Integration/PandaGo sandbox acceptance returned a persisted PANDAGO delivery quote, NGN26.00. Temporary validation address removed through the owner API. Initial disabled/packaging/internal-auth failures and one transient provider failure were preserved in `.build/pandago/` logs; a subsequent safe quote succeeded. Java adapter OAuth/outlet, fee/time and quote calls also passed inside Integration. No delivery booking or payment created. No commit.
