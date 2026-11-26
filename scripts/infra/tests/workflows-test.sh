#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
workflows=(infra-plan.yml infra-artifacts.yml deploy-environment.yml aws-first-account-bootstrap.yml aws-first-account-validate.yml)

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
deploy="$repo_root/.github/workflows/deploy-environment.yml"
grep -Eq '"\.github/workflows/deploy-environment.yml"' "$plan"
grep -Eq '"services/\*\*/Dockerfile"' "$plan"
grep -Eq '"infrastructure/docker/\*\*"' "$plan"
grep -Eq "if: github.event_name == 'workflow_dispatch'" "$plan"
grep -Eq 'terraform .* plan' "$plan"
if grep -Eq 'terraform .* apply' "$plan"; then
  echo "infrastructure plan workflow must never apply" >&2
  exit 1
fi
[[ "$(grep -Ec 'id-token: write' "$plan")" == 1 ]]
grep -Eq 'environment: \$\{\{ inputs.environment \}\}' "$plan"
grep -Eq 'permissions:' "$artifacts"
grep -Eq 'id-token: write' "$artifacts"
grep -Eq 'scripts/infra/publish-images.sh' "$artifacts"
if grep -Eq 'ecs:UpdateService|terraform[[:space:]]+apply' "$artifacts"; then
  echo "image publisher must not deploy or apply infrastructure" >&2
  exit 1
fi
grep -Eq 'options: \[stage, prod\]' "$deploy"
grep -Eq 'environment: \$\{\{ inputs.environment \}\}' "$deploy"
grep -Eq 'TARGET_ENVIRONMENT: \$\{\{ inputs.environment \}\}' "$deploy"
grep -Eq 'id-token: write' "$deploy"
grep -Eq 'docs/releases/\$\{TARGET_ENVIRONMENT\}' "$deploy"
grep -Eq 'deploy-environment\.sh "\$TARGET_ENVIRONMENT"' "$deploy"
if grep -Eq 'deploy-stage\.sh|deploy-stage\.yml' "$deploy" "$plan"; then
  echo "stage-only deployment tooling must not remain in the environment workflow" >&2
  exit 1
fi
if grep -Eq 'terraform|secretsmanager|iam:[[:space:]]*\*' "$deploy"; then
  echo "environment release workflow must not provision infrastructure or read secrets" >&2
  exit 1
fi

echo "Workflow syntax, pinned actions, and permission boundaries PASS"

ruby - "$repo_root/.github/workflows/aws-first-account-bootstrap.yml" <<'RUBY'
require 'yaml'
workflow = YAML.load_file(ARGV.fetch(0))
triggers = workflow['on'] || workflow[true]
raise 'bootstrap credentials must be manual-only' unless triggers.keys == ['workflow_dispatch']
raise 'unexpected bootstrap operations' unless triggers['workflow_dispatch']['inputs']['action']['options'] == ['bootstrap', 'plan', 'apply']
raise 'bootstrap must not require a pre-existing OIDC role' unless workflow['permissions'] == {'contents' => 'read'}
raise 'state mutation runs must serialize' unless workflow['concurrency'] == {'group' => 'nigson-infrastructure-stage', 'cancel-in-progress' => false}
job = workflow['jobs']['first-account']
raise 'bootstrap must use staging environment' unless job['environment'] == 'stage'
raise 'bootstrap must use default-branch code' unless job['if'].include?('github.event.repository.default_branch')
raise 'account must come from the documented secret' unless job['env']['AWS_EXPECTED_ACCOUNT_ID'] == '${{ secrets.AWS_ACCOUNT_ID }}'
raise 'do not upload Terraform state/plan artifacts' if job['steps'].any? { |step| step.fetch('uses', '').include?('upload-artifact') }
raise 'do not interpolate dispatch input into shell code' if job['steps'].any? { |step| step.fetch('run', '').include?('${{ inputs.') }
puts 'First-account manual dispatch, staging secrets and durable state boundaries PASS'
RUBY
