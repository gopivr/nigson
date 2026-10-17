#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: deploy-stage.sh <reviewed-release.json> <rollback-task-definitions.json>" >&2
  exit 2
fi
release_file=$1
rollback_file=$2
[[ -f "$release_file" && -f "$rollback_file" ]]
[[ -n "${AWS_REGION:-}" && "${AWS_EXPECTED_ACCOUNT_ID:-}" =~ ^[0-9]{12}$ ]]
for command in aws jq curl; do command -v "$command" >/dev/null; done

actual_account=$(aws sts get-caller-identity --query Account --output text --no-cli-pager)
[[ "$actual_account" == "$AWS_EXPECTED_ACCOUNT_ID" ]]

release=$(jq -e '
  .environment == "stage" and
  (.release_id | type == "string" and test("^[A-Za-z0-9._-]{1,80}$")) and
  (.cluster_arn | type == "string" and test("^arn:aws[a-z-]*:ecs:[a-z0-9-]+:[0-9]{12}:cluster/[A-Za-z0-9_-]+$")) and
  (.product_phase | type == "number" and . >= 1 and . <= 5) and
  ((.owners | type == "array") and ((.owners | sort) == ["commerce", "experience", "integration"] or (.product_phase == 5 and (.owners | sort) == ["ai", "commerce", "experience", "integration"]))) and
  (.services | type == "object" and length >= 3) and
  ((.migration_tasks | type == "object") and ((.migration_tasks | keys | sort) == (.owners | sort))) and
  (.health_urls | type == "array" and length >= 1) and
  all(.services | to_entries[]; (.key | IN("commerce", "experience", "integration", "ai")) and (.value.task_definition | type == "string" and test("^arn:aws[a-z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/nigson-stage-[a-z]+:[0-9]+$")) and (.value.image | type == "string" and test("^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/nigson/stage/[a-z]+@sha256:[a-f0-9]{64}$"))) and
  all(.migration_tasks | to_entries[]; (.value.task_arn | type == "string" and test("^arn:aws[a-z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task/[A-Za-z0-9_-]+/[A-Fa-f0-9]+$")) and (.value.exit_code == 0)) and
  (([.migration_tasks[] | .task_arn] | unique | length) == (.migration_tasks | length)) and
  all(.health_urls[]; type == "string" and test("^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~/-]*)?$"))
' "$release_file" >/dev/null && jq -c . "$release_file")
rollback=$(jq -e '
  .environment == "stage" and (.services | type == "object" and length >= 3) and
  all(.services | to_entries[]; (.value | type == "string" and test("^arn:aws[a-z-]*:ecs:" + $region + ":" + $account + ":task-definition/nigson-stage-[a-z]+:[0-9]+$")))
' --arg region "$AWS_REGION" --arg account "$AWS_EXPECTED_ACCOUNT_ID" "$rollback_file" >/dev/null && jq -c . "$rollback_file")

release_services=$(jq -c '.services | keys | sort' <<<"$release")
rollback_services=$(jq -c '.services | keys | sort' <<<"$rollback")
[[ "$release_services" == "$rollback_services" ]]

region=$AWS_REGION
cluster=$(jq -er .cluster_arn <<<"$release")
[[ "$cluster" == *":$AWS_REGION:$AWS_EXPECTED_ACCOUNT_ID:cluster/"* ]]
owners=$(jq -c '.owners | sort' <<<"$release")
services=$(jq -c '.services | keys | sort' <<<"$release")
[[ "$services" == "$owners" ]]

# Verify every successful migration receipt before starting any service rollout.
while IFS= read -r task; do
  [[ "$task" == *":$AWS_REGION:$AWS_EXPECTED_ACCOUNT_ID:task/"* ]]
  detail=$(aws ecs describe-tasks --region "$region" --cluster "$cluster" --tasks "$task" --no-cli-pager --output json)
  [[ "$(jq -r '.tasks[0].lastStatus' <<<"$detail")" == "STOPPED" ]]
  [[ "$(jq -r '[.tasks[0].containers[] | select(.name == "owner-migration") | .exitCode][0] // empty' <<<"$detail")" == "0" ]]
done < <(jq -r '.migration_tasks[] | .task_arn' <<<"$release")

while IFS=$'\t' read -r service task_definition image; do
  [[ "$task_definition" == *":$AWS_REGION:$AWS_EXPECTED_ACCOUNT_ID:task-definition/"* ]]
  task=$(aws ecs describe-task-definition --region "$region" --task-definition "$task_definition" --no-cli-pager --output json)
  configured_image=$(jq -er --arg service "$service" '[.taskDefinition.containerDefinitions[] | select(.name == $service) | .image][0]' <<<"$task")
  [[ "$configured_image" == "$image" ]]
  [[ "$image" == *"@sha256:"* ]]
  current=$(aws ecs describe-services --region "$region" --cluster "$cluster" --services "nigson-stage-$service" --no-cli-pager --output json)
  [[ "$(jq -r '.failures | length' <<<"$current")" == "0" ]]
done < <(jq -r '.services | to_entries[] | [.key, .value.task_definition, .value.image] | @tsv' <<<"$release")

changed_services=()
rollout_error=0
while IFS=$'\t' read -r service task_definition image; do
  changed_services+=("$service")
  if ! aws ecs update-service --region "$region" --cluster "$cluster" --service "nigson-stage-$service" --task-definition "$task_definition" --force-new-deployment --no-cli-pager >/dev/null; then
    rollout_error=1
    break
  fi
done < <(jq -r '.services | to_entries[] | [.key, .value.task_definition, .value.image] | @tsv' <<<"$release")

rollback_required=$rollout_error
for service in "${changed_services[@]}"; do
  if ! aws ecs wait services-stable --region "$region" --cluster "$cluster" --services "nigson-stage-$service" --no-cli-pager; then
    rollback_required=1
    break
  fi
done
if (( rollback_required == 0 )); then
  while IFS= read -r url; do
    status=$(curl --fail --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 10 "$url") || { rollback_required=1; break; }
    [[ "$status" =~ ^2[0-9][0-9]$ ]] || { rollback_required=1; break; }
  done < <(jq -r '.health_urls[]' <<<"$release")
fi

if (( rollback_required != 0 )); then
  echo "Stage rollout or health check failed for release $(jq -r .release_id <<<"$release"); applying reviewed rollback task definitions." >&2
  for service in "${changed_services[@]}"; do
    previous=$(jq -er --arg service "$service" '.services[$service]' <<<"$rollback")
    if ! aws ecs update-service --region "$region" --cluster "$cluster" --service "nigson-stage-$service" --task-definition "$previous" --force-new-deployment --no-cli-pager >/dev/null; then
      echo "Rollback request failed for nigson-stage-$service; operator intervention required." >&2
      continue
    fi
    if ! aws ecs wait services-stable --region "$region" --cluster "$cluster" --services "nigson-stage-$service" --no-cli-pager; then
      echo "Rollback stability is unconfirmed for nigson-stage-$service; operator intervention required." >&2
    fi
  done
  exit 1
fi

printf 'Stage rollout healthy: release=%s services=%s\n' "$(jq -r .release_id <<<"$release")" "${changed_services[*]}"
