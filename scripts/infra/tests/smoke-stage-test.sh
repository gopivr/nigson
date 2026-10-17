#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
mkdir -p "$temporary_dir/bin"
cat > "$temporary_dir/bin/curl" <<'MOCK'
#!/usr/bin/env bash
url="${*: -1}"
if [[ "$url" == */internal* ]]; then
  printf '%s' "${DENIAL_STATUS:-404}"
else
  printf '%s' "${PRIVATE_STATUS:-200}"
fi
MOCK
chmod +x "$temporary_dir/bin/curl"
export PATH="$temporary_dir/bin:$PATH"
spec="$temporary_dir/smoke.json"
cat > "$spec" <<'JSON'
{
  "environment":"stage","release_id":"probe-1",
  "private_targets":[{"name":"commerce-readiness","url":"https://commerce.svc.stage.nigson.internal/actuator/health/readiness","expected_status":200}],
  "public_denials":[{"name":"internal-root","url":"https://api.stage.example.invalid/internal","expected_status":404},{"name":"internal-child","url":"https://api.stage.example.invalid/internal/health","expected_status":404}]
}
JSON
bash "$repo_root/scripts/infra/smoke-stage.sh" "$spec" >/dev/null
if DENIAL_STATUS=200 bash "$repo_root/scripts/infra/smoke-stage.sh" "$spec" >/dev/null 2>&1; then
  echo "expected public internal route to reject non-404 response" >&2; exit 1
fi
if PRIVATE_STATUS=000 bash "$repo_root/scripts/infra/smoke-stage.sh" "$spec" >/dev/null 2>&1; then
  echo "expected private route connectivity failure" >&2; exit 1
fi
jq '.public_denials[0].url = "https://api.stage.example.invalid/health"' "$spec" > "$temporary_dir/invalid.json"
if bash "$repo_root/scripts/infra/smoke-stage.sh" "$temporary_dir/invalid.json" >/dev/null 2>&1; then
  echo "expected non-internal public denial URL rejection" >&2; exit 1
fi
echo "Stage smoke probe tests PASS"
