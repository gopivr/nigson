#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 2 || ! -f "$2" ]]; then
  echo "usage: smoke-environment.sh <stage|prod> <approved-smoke.json>" >&2
  exit 2
fi
environment=$1
[[ "$environment" == "stage" || "$environment" == "prod" ]]
for command in curl jq; do command -v "$command" >/dev/null; done
spec=$(jq -e '
  .environment == $environment and
  (.release_id | type == "string" and test("^[A-Za-z0-9._-]{1,80}$")) and
  (.private_targets | type == "array" and length >= 1) and
  (.public_denials | type == "array" and length >= 1) and
  all(.private_targets[]; (.url | type == "string" and test("^https://[a-z0-9-]+\\.svc\\." + $environment + "\\.nigson\\.internal(/[A-Za-z0-9._~/-]*)?$")) and (.expected_status == 200)) and
  all(.public_denials[]; (.url | type == "string" and test("^https://[A-Za-z0-9.-]+/internal(/[^?#]*)?$")) and (.expected_status == 404))
' --arg environment "$environment" "$2" >/dev/null && jq -c . "$2")
probe() {
  local label=$1 url=$2 expected=$3 status
  status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --connect-timeout 5 --max-time 15 "$url") || {
    echo "$label probe failed to connect: $url" >&2
    return 1
  }
  if [[ "$status" != "$expected" ]]; then
    echo "$label probe returned HTTP $status; expected $expected: $url" >&2
    return 1
  fi
  printf '%s PASS status=%s url=%s\n' "$label" "$status" "$url"
}
while IFS=$'\t' read -r label url status; do probe private "$url" "$status"; done < <(jq -r '.private_targets[] | [.name, .url, .expected_status] | @tsv' <<<"$spec")
while IFS=$'\t' read -r label url status; do probe public-internal-denial "$url" "$status"; done < <(jq -r '.public_denials[] | [.name, .url, .expected_status] | @tsv' <<<"$spec")
printf '%s smoke probes PASS: release=%s\n' "$environment" "$(jq -r .release_id <<<"$spec")"
