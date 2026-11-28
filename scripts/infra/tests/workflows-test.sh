#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
workflows=(backend-infrastructure-validate.yml backend-images-publish.yml backend-deploy.yml backend-aws-infrastructure.yml frontend-deploy.yml)

ruby -e 'require "yaml"; ARGV.each { |file| YAML.load_file(file) }' \
  "${workflows[@]/#/$repo_root/.github/workflows/}"

for workflow in "${workflows[@]}"; do
  file="$repo_root/.github/workflows/$workflow"
  while IFS= read -r line; do
    [[ "$line" =~ uses:[[:space:]] ]] || continue
    [[ "$line" == *"uses: ./.github/workflows/"* ]] && continue
    ref=${line##*@}
    [[ "$ref" =~ ^[0-9a-f]{40}$ ]] || { echo "un-pinned action reference in $workflow" >&2; exit 1; }
  done < "$file"
done

plan="$repo_root/.github/workflows/backend-infrastructure-validate.yml"
artifacts="$repo_root/.github/workflows/backend-images-publish.yml"
deploy="$repo_root/.github/workflows/backend-deploy.yml"
ruby - "$plan" <<'RUBY'
require 'yaml'
w = YAML.load_file(ARGV.fetch(0))
raise 'validation must use read-only permissions' unless w['permissions'] == {'contents' => 'read'}
raise 'keep general offline checks after removing legacy remote plan' unless w['jobs'].key?('offline-validation')
raise 'validation must not use AWS secrets or OIDC' if File.read(ARGV.fetch(0)).match?(/\$\{\{\s*secrets\.|id-token: write/)
RUBY
if grep -Eq 'terraform .* apply' "$plan"; then
  echo "validation workflow must never apply" >&2
  exit 1
fi
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
grep -Eq 'deploy-latest-backend\.py "\$TARGET_ENVIRONMENT"' "$deploy"
if grep -Eq 'deploy-stage\.sh|deploy-stage\.yml' "$deploy" "$plan"; then
  echo "stage-only deployment tooling must not remain in the environment workflow" >&2
  exit 1
fi
if grep -Eq 'secretsmanager|iam:[[:space:]]*\*' "$deploy"; then
  echo "environment release workflow must not provision infrastructure or read secrets" >&2
  exit 1
fi

echo "Workflow syntax, pinned actions, and permission boundaries PASS"

ruby - "$repo_root/.github/workflows/backend-aws-infrastructure.yml" <<'RUBY'
require 'yaml'
workflow = YAML.load_file(ARGV.fetch(0))
triggers = workflow['on'] || workflow[true]
raise 'bootstrap credentials must be manual-only' unless triggers.keys == ['workflow_dispatch']
raise 'unexpected bootstrap operations' unless triggers['workflow_dispatch']['inputs']['action']['options'] == ['bootstrap', 'plan', 'apply', 'runtime-plan', 'runtime-apply']
raise 'bootstrap must not require a pre-existing OIDC role' unless workflow['permissions'] == {'contents' => 'read'}
raise 'state mutation runs must serialize' unless workflow['concurrency'] == {'group' => 'nigson-stage-deployment', 'cancel-in-progress' => false}
job = workflow['jobs']['first-account']
raise 'bootstrap must use staging environment' unless job['environment'] == 'stage'
raise 'bootstrap must use default-branch code' unless job['if'].include?('github.event.repository.default_branch')
raise 'account must come from the documented secret' unless job['env']['AWS_EXPECTED_ACCOUNT_ID'] == '${{ secrets.AWS_ACCOUNT_ID }}'
raise 'do not upload Terraform state/plan artifacts' if job['steps'].any? { |step| step.fetch('uses', '').include?('upload-artifact') && step.dig('with', 'path') != '.build/backend-deploy/deployment.json' }
raise 'runtime artifact lookup requires read-only Actions access' unless job['permissions'] == {'contents' => 'read', 'actions' => 'read'}
raise 'foundation must not run in runtime actions' unless job['steps'].find { |s| s['name'] == 'Bootstrap or provision the first-account platform' }['if'].include?('!startsWith')
raise 'do not interpolate dispatch input into shell code' if job['steps'].any? { |step| step.fetch('run', '').include?('${{ inputs.') }
puts 'First-account manual dispatch, staging secrets and durable state boundaries PASS'
RUBY

ruby - "$artifacts" <<'RUBY'
require 'yaml'
w = YAML.load_file(ARGV.fetch(0))
job = w['jobs']['publish']
raise 'publisher must run from default branch' unless job['if'].include?('default_branch')
raise 'bootstrap account secret must work without another account variable' unless job['env']['AWS_EXPECTED_ACCOUNT_ID'] == '${{ vars.AWS_EXPECTED_ACCOUNT_ID || secrets.AWS_ACCOUNT_ID }}'
config = job['steps'].find { |s| s['id'] == 'configuration' }
raise 'role must use deterministic bootstrap name' unless config['run'].include?('role/nigson-${TARGET_ENVIRONMENT}-github-publish')
auth = job['steps'].find { |s| s.fetch('uses', '').start_with?('aws-actions/configure-aws-credentials@') }
raise 'publisher must use the resolved OIDC role' unless auth['with']['role-to-assume'] == '${{ steps.configuration.outputs.role_arn }}'
raise 'publisher must not use static credentials' if auth['with'].keys.any? { |k| ['aws-access-key-id', 'aws-secret-access-key'].include?(k) }
builder = job['steps'].find { |s| s['id'] == 'buildx' }
raise 'attestations require the Docker container builder' unless builder && builder.fetch('uses', '').start_with?('docker/setup-buildx-action@') && builder['with'] == {'driver' => 'docker-container', 'use' => true}
publication = job['steps'].find { |s| s.fetch('run', '').include?('scripts/infra/publish-images.sh') }
raise 'publication must use the selected attestation builder' unless publication.dig('env', 'BUILDX_BUILDER') == '${{ steps.buildx.outputs.name }}' && job['steps'].index(builder) < job['steps'].index(publication)
report = job['steps'].find { |s| s.dig('with', 'path') == '.build/image-scans/*.json' }
raise 'vulnerability details must survive gate failures without exporting other files' unless report && report['if'] == 'always()' && report.dig('with', 'path') == '.build/image-scans/*.json'
release = job['steps'].find { |s| s.dig('with', 'path') == '.build/backend-release/release.json' }
raise 'only successful builds may publish the deployable cohort' unless release && release['if'].nil? && release['with']['name'].include?('inputs.environment')
puts 'Bootstrap-managed publishing role discovery and short-lived credential wiring PASS'
RUBY

ruby - "$deploy" <<'RUBY'
require 'yaml'
w = YAML.load_file(ARGV.fetch(0))
job = w['jobs']['deploy']
raise 'deployment must run from default branch' unless job['if'].include?('default_branch')
raise 'deployment must accept the bootstrap account secret' unless job['env']['AWS_EXPECTED_ACCOUNT_ID'] == '${{ vars.AWS_EXPECTED_ACCOUNT_ID || secrets.AWS_ACCOUNT_ID }}'
config = job['steps'].find { |s| s['id'] == 'configuration' }
raise 'deployment role must match the bootstrap-managed name' unless config['run'].include?('role/nigson-${TARGET_ENVIRONMENT}-github-deploy')
auth = job['steps'].find { |s| s.fetch('uses', '').start_with?('aws-actions/configure-aws-credentials@') }
raise 'deployment must use resolved OIDC role' unless auth['with']['role-to-assume'] == '${{ steps.configuration.outputs.role_arn }}'
raise 'deployment must not use static keys or manual role variables' if File.read(ARGV.fetch(0)).match?(/AWS_DEPLOY_ROLE_ARN|aws-access-key-id|aws-secret-access-key/)
triggers = w['on'] || w[true]
raise 'only environment should be required to deploy' unless triggers['workflow_dispatch']['inputs'].keys == ['environment']
raise 'artifact lookup requires read-only Actions access' unless job.dig('permissions', 'actions') == 'read'
download = job['steps'].find { |s| s.fetch('uses', '').start_with?('actions/download-artifact@') }
raise 'release must be selected by immutable build/run IDs' unless download.dig('with', 'artifact-ids') == '${{ steps.release.outputs.artifact_id }}' && download.dig('with', 'run-id') == '${{ steps.release.outputs.run_id }}'
raise 'do not upload sensitive task-template payloads' unless job['steps'].find { |s| s.fetch('uses', '').start_with?('actions/upload-artifact@') }.dig('with', 'path') == '.build/backend-deploy/deployment.json'
puts 'Bootstrap-managed deployment role discovery and OIDC credential wiring PASS'
RUBY
