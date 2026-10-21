#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
workflows=(infra-plan.yml infra-artifacts.yml deploy-stage.yml)

ruby -e 'require "yaml"; ARGV.each { |file| YAML.load_file(file) }' \
  "${workflows[@]/#/$repo_root/.github/workflows/}"

for workflow in "${workflows[@]}"; do
  file="$repo_root/.github/workflows/$workflow"
  while IFS= read -r line; do
    [[ "$line" =~ uses:[[:space:]] ]] || continue
    ref=${line##*@}
    [[ "$ref" =~ ^[0-9a-f]{40}$ ]] || { echo "un-pinned action reference in $workflow" >&2; exit 1; }
  done < "$file"
done

plan="$repo_root/.github/workflows/infra-plan.yml"
artifacts="$repo_root/.github/workflows/infra-artifacts.yml"
deploy="$repo_root/.github/workflows/deploy-stage.yml"
rg -q '"\.github/workflows/deploy-stage.yml"' "$plan"
rg -q '"services/\*\*/Dockerfile"' "$plan"
rg -q '"infrastructure/docker/\*\*"' "$plan"
rg -q "if: github.event_name == 'workflow_dispatch'" "$plan"
rg -q 'terraform .* plan' "$plan"
if rg -q 'terraform .* apply' "$plan"; then
  echo "infrastructure plan workflow must never apply" >&2
  exit 1
fi
[[ "$(rg -c 'id-token: write' "$plan")" == 1 ]]
rg -q 'environment: \$\{\{ inputs.environment \}\}' "$plan"
rg -q 'permissions:' "$artifacts"
rg -q 'id-token: write' "$artifacts"
rg -q 'scripts/infra/publish-images.sh' "$artifacts"
if rg -q 'ecs:UpdateService|terraform[[:space:]]+apply' "$artifacts"; then
  echo "image publisher must not deploy or apply infrastructure" >&2
  exit 1
fi
rg -q 'environment: stage' "$deploy"
rg -q 'id-token: write' "$deploy"
rg -q 'RELEASE_MANIFEST.*docs/releases/stage' "$deploy"
rg -q 'scripts/infra/deploy-stage.sh' "$deploy"
if rg -q 'terraform|secretsmanager|iam:[[:space:]]*\*' "$deploy"; then
  echo "stage release workflow must not provision infrastructure or read secrets" >&2
  exit 1
fi

echo "Workflow syntax, pinned actions, and permission boundaries PASS"
