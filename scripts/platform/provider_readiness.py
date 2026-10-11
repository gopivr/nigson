"""Pure fail-closed validation before genuine qualification can build or use HTTP."""
import re

SYNTHETIC_MARKERS = (
    "mock", "dummy", "placeholder", "example", "synthetic", "fixture",
    "configuration_validation", "shape_only", "changeme", "your_secret", "test_key",
)
KEYS = {
    "paystack": ("PAYSTACK_MODE", "PAYSTACK_BASE_URL", "PAYSTACK_SECRET_KEY",
                 "PAYSTACK_QUALIFICATION_REFERENCE", "PAYSTACK_QUALIFICATION_KOBO"),
    "fedex": ("FEDEX_MODE", "FEDEX_BASE_URL", "FEDEX_CLIENT_ID", "FEDEX_CLIENT_SECRET",
              "FEDEX_ACCOUNT_NUMBER", "FEDEX_QUALIFICATION_REQUEST"),
    "twilio": ("TWILIO_MODE", "TWILIO_BASE_URL", "TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN",
               "TWILIO_MESSAGING_SERVICE_SID", "TWILIO_QUALIFICATION_MESSAGE_SID"),
}

def validate(env):
    """Return selected providers; errors contain setting names only, never values."""
    if env.get("ENV") != "stage":
        raise ValueError("explicit ENV=stage required")
    selected = env.get("PROVIDER", "all")
    if selected not in (*KEYS, "all"):
        raise ValueError("unknown PROVIDER")
    names = tuple(KEYS) if selected == "all" else (selected,)
    for name in names:
        for setting in KEYS[name]:
            value = env.get(setting, "")
            if (not value or len(value) > 2048 or value != value.strip()
                    or any(ord(c) < 32 for c in value)
                    or any(marker in value.lower() for marker in SYNTHETIC_MARKERS)):
                raise ValueError("genuine " + setting + " required")
    if "paystack" in names:
        if env["PAYSTACK_MODE"] != "TEST":
            raise ValueError("PAYSTACK_MODE must be TEST for read-only qualification")
        if env["PAYSTACK_BASE_URL"] not in ("https://api.paystack.co", "https://api.paystack.co/"):
            raise ValueError("PAYSTACK_BASE_URL must be the official HTTPS endpoint")
        key = env["PAYSTACK_SECRET_KEY"]
        if not re.fullmatch(r"sk_test_[A-Za-z0-9_]+", key) or re.fullmatch(r"sk_test_0+", key):
            raise ValueError("genuine PAYSTACK_SECRET_KEY required")
        if not re.fullmatch(r"[A-Za-z0-9.=_-]{1,100}", env["PAYSTACK_QUALIFICATION_REFERENCE"]):
            raise ValueError("PAYSTACK_QUALIFICATION_REFERENCE invalid")
        kobo = env["PAYSTACK_QUALIFICATION_KOBO"]
        if not re.fullmatch(r"[1-9][0-9]{0,18}", kobo) or int(kobo) > 9223372036854775807:
            raise ValueError("PAYSTACK_QUALIFICATION_KOBO must be positive bounded integer")
    if "fedex" in names and env["FEDEX_MODE"] != "TEST":
        raise ValueError("FEDEX_MODE must be TEST for read-only qualification")
    if "twilio" in names and env["TWILIO_MODE"] not in ("TEST", "LIVE"):
        raise ValueError("TWILIO_MODE must select a genuine account")
    return names
