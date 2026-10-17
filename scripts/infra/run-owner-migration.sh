#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: run-owner-migration.sh <stage|prod> <commerce|experience|integration|ai>" >&2
  exit 2
fi
environment=$1
owner=$2
[[ "$environment" == "stage" || "$environment" == "prod" ]]
[[ "$owner" == "commerce" || "$owner" == "experience" || "$owner" == "integration" || "$owner" == "ai" ]]
[[ -n "${AWS_REGION:-}" && "${AWS_EXPECTED_ACCOUNT_ID:-}" =~ ^[0-9]{12}$ ]]

root="infrastructure/terraform/environments/${environment}"
outputs=$(terraform -chdir="$root" output -json migrations)
[[ "$(jq -r .environment <<<"$outputs")" == "$environment" ]]
[[ "$(jq -r .region <<<"$outputs")" == "$AWS_REGION" ]]
task_definition=$(jq -er --arg owner "$owner" '.task_definition_arns[$owner]' <<<"$outputs")
cluster=$(jq -er .cluster_arn <<<"$outputs")
security_group=$(jq -er .security_group_id <<<"$outputs")
subnets=$(jq -ce '.private_subnet_ids | select(length >= 2)' <<<"$outputs")

actual_account=$(aws sts get-caller-identity --query Account --output text --no-cli-pager)
[[ "$actual_account" == "$AWS_EXPECTED_ACCOUNT_ID" ]]

temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT
chmod 700 "$temporary_dir"
cat > "$temporary_dir/network.json" <<NETWORK
{"awsvpcConfiguration":{"subnets":${subnets},"securityGroups":["${security_group}"],"assignPublicIp":"DISABLED"}}
NETWORK
chmod 600 "$temporary_dir/network.json"

response="$temporary_dir/run-task.json"
aws ecs run-task \
  --region "$AWS_REGION" \
  --cluster "$cluster" \
  --task-definition "$task_definition" \
  --launch-type FARGATE \
  --network-configuration "file://${temporary_dir}/network.json" \
  --started-by "nigson-${owner}-migration" \
  --no-cli-pager \
  --output json > "$response"

failure_count=$(jq '(.failures // []) | length' "$response")
if (( failure_count != 0 )); then
  echo "ECS rejected the migration task launch; inspect the AWS task event. No retry was attempted." >&2
  exit 1
fi
task_arn=$(jq -er '.tasks | select(length == 1) | .[0].taskArn' "$response")
if ! aws ecs wait tasks-stopped --region "$AWS_REGION" --cluster "$cluster" --tasks "$task_arn" --no-cli-pager; then
  echo "Migration task has not reached a confirmed stopped state; task=${task_arn}. Do not retry until its state and database changelog are inspected." >&2
  exit 1
fi

details=$(aws ecs describe-tasks --region "$AWS_REGION" --cluster "$cluster" --tasks "$task_arn" --no-cli-pager --output json)
exit_code=$(jq -r '[.tasks[0].containers[] | select(.name == "owner-migration") | .exitCode][0] // empty' <<<"$details")
if [[ "$exit_code" != "0" ]]; then
  echo "Owner migration failed or has no final container exit code; task=${task_arn}. Inspect its CloudWatch log and databasechangelog before any retry." >&2
  exit 1
fi
printf 'Owner migration completed: owner=%s task=%s\n' "$owner" "$task_arn"
