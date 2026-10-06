"""Shell-free local container readiness probe."""

import json
import urllib.request

with urllib.request.urlopen("http://127.0.0.1:8084/health/ready", timeout=2) as response:
    if response.status != 200 or json.load(response) != {"status": "UP"}:
        raise SystemExit(1)
