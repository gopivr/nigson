#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
dockerfiles=(
  services/commerce/Dockerfile
  services/experience/Dockerfile
  services/integration/Dockerfile
  services/ai/Dockerfile
  infrastructure/docker/keycloak/Dockerfile
  infrastructure/docker/keycloak/Operations.Dockerfile
)

for path in "${dockerfiles[@]}"; do
  file="$repo_root/$path"
  [[ -f "$file" ]] || { echo "missing production Dockerfile: $path" >&2; exit 1; }
  while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*FROM[[:space:]] ]] || continue
    base=$(awk '{for (i=2; i<=NF; i++) if ($i !~ /^--/) {print $i; exit}}' <<<"$line")
    case "$base" in
      build|runtime-base|builder|uv) continue ;;
    esac
    [[ "$base" == *@sha256:* ]] || { echo "unpinned external base image in $path" >&2; exit 1; }
  done < "$file"
done

for path in services/commerce/Dockerfile services/experience/Dockerfile services/integration/Dockerfile services/ai/Dockerfile infrastructure/docker/keycloak/Operations.Dockerfile; do
  rg -q '^USER 10001:10001$' "$repo_root/$path" || { echo "non-root runtime user missing in $path" >&2; exit 1; }
done
rg -q '^USER 1000:0$' "$repo_root/infrastructure/docker/keycloak/Dockerfile"
rg -q '^CMD \["start", "--optimized"\]$' "$repo_root/infrastructure/docker/keycloak/Dockerfile"
if rg -qi 'start-dev|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC )?PRIVATE KEY-----' "${dockerfiles[@]/#/$repo_root/}"; then
  echo "development mode or credential material found in production Dockerfiles" >&2
  exit 1
fi
rg -q 'readonlyRootFilesystem = true' "$repo_root/infrastructure/terraform/modules/compute/main.tf"
rg -q 'readonlyRootFilesystem = true' "$repo_root/infrastructure/terraform/modules/identity/main.tf"

echo "Production Dockerfile input checks PASS"
