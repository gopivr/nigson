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

write_spec() {
  local environment=$1
  jq -n --arg environment "$environment" '{
    environment:$environment,release_id:"probe-1",
    private_targets:[{name:"commerce-readiness",url:("https://commerce.svc." + $environment + ".nigson.internal/actuator/health/readiness"),expected_status:200}],
    public_denials:[{name:"internal-root",url:("https://api." + $environment + ".example.invalid/internal"),expected_status:404},{name:"internal-child",url:("https://api." + $environment + ".example.invalid/internal/health"),expected_status:404}]
  }' > "$temporary_dir/smoke-${environment}.json"
}

for environment in stage prod; do
  spec="$temporary_dir/smoke-${environment}.json"
  write_spec "$environment"
  bash "$repo_root/scripts/infra/smoke-environment.sh" "$environment" "$spec" >/dev/null
  if DENIAL_STATUS=200 bash "$repo_root/scripts/infra/smoke-environment.sh" "$environment" "$spec" >/dev/null 2>&1; then
    echo "expected public internal route to reject non-404 response for $environment" >&2; exit 1
  fi
  if PRIVATE_STATUS=000 bash "$repo_root/scripts/infra/smoke-environment.sh" "$environment" "$spec" >/dev/null 2>&1; then
    echo "expected private route connectivity failure for $environment" >&2; exit 1
  fi
  jq '.public_denials[0].url = ("https://api." + .environment + ".example.invalid/health")' "$spec" > "$temporary_dir/invalid-${environment}.json"
  if bash "$repo_root/scripts/infra/smoke-environment.sh" "$environment" "$temporary_dir/invalid-${environment}.json" >/dev/null 2>&1; then
    echo "expected non-internal public denial URL rejection for $environment" >&2; exit 1
  fi

  other=stage
  [[ "$environment" == stage ]] && other=prod
  jq --arg other "$other" '.environment = $other' "$spec" > "$temporary_dir/wrong-${environment}.json"
  if bash "$repo_root/scripts/infra/smoke-environment.sh" "$environment" "$temporary_dir/wrong-${environment}.json" >/dev/null 2>&1; then
    echo "expected cross-environment smoke spec rejection for $environment" >&2; exit 1
  fi
done

if bash "$repo_root/scripts/infra/smoke-environment.sh" development "$temporary_dir/smoke-stage.json" >/dev/null 2>&1; then
  echo "expected unsupported environment rejection" >&2; exit 1
fi
echo "Stage and production smoke probe tests PASS"
