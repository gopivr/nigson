#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
mkdir -p "$temporary_dir/bin"
cat > "$temporary_dir/bin/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$AWS_CALL_LOG"
case "$*" in
  "sts get-caller-identity"*) echo 123456789012 ;;
  "ecs describe-tasks"*) printf '{"tasks":[{"lastStatus":"STOPPED","containers":[{"name":"owner-migration","exitCode":0}]}],"failures":[]}' ;;
  "ecs describe-task-definition"*)
    taskdef="$(printf '%s\n' "$*" | sed -E 's/.*--task-definition ([^ ]+).*/\1/')"
    service="${taskdef#*task-definition/}"
    service="${service#nigson-stage-}"
    service="${service%%:*}"
    image="${IMAGE_PREFIX}${service}@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    jq -n --arg image "$image" --arg service "$service" '{taskDefinition:{containerDefinitions:[{name:$service,image:$image}]}}'
    ;;
  "ecs describe-services"*) echo '{"failures":[],"services":[{}]}' ;;
  "ecs update-service"*)
    if [[ "${FAIL_UPDATE:-0}" == 1 && "$*" == *"task-definition/nigson-stage-commerce:5"* ]]; then exit 42; fi
    echo '{}'
    ;;
  "ecs wait services-stable"*) [[ "${FAIL_STABLE:-0}" != 1 ]] ;;
  *) echo "unexpected aws command: $*" >&2; exit 90 ;;
esac
MOCK
cat > "$temporary_dir/bin/terraform" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
cat > "$temporary_dir/bin/curl" <<'MOCK'
#!/usr/bin/env bash
[[ "${FAIL_HEALTH:-0}" != 1 ]] || exit 22
printf 200
MOCK
chmod +x "$temporary_dir/bin/"*
export PATH="$temporary_dir/bin:$PATH"
export AWS_REGION=eu-west-1 AWS_EXPECTED_ACCOUNT_ID=123456789012 AWS_CALL_LOG="$temporary_dir/aws.log"
export IMAGE_PREFIX=123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/
release="$temporary_dir/release.json"
rollback="$temporary_dir/rollback.json"
cat > "$release" <<'JSON'
{
  "environment":"stage","release_id":"test-release","product_phase":1,
  "cluster_arn":"arn:aws:ecs:eu-west-1:123456789012:cluster/nigson-stage",
  "owners":["commerce","experience","integration"],
  "migration_tasks":{
    "commerce":{"task_arn":"arn:aws:ecs:eu-west-1:123456789012:task/nigson-stage/abcdef0123456789","exit_code":0},
    "experience":{"task_arn":"arn:aws:ecs:eu-west-1:123456789012:task/nigson-stage/abcdef0123456790","exit_code":0},
    "integration":{"task_arn":"arn:aws:ecs:eu-west-1:123456789012:task/nigson-stage/abcdef0123456791","exit_code":0}
  },
  "services":{
    "commerce":{"task_definition":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-commerce:5","image":"123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/commerce@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
    "experience":{"task_definition":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-experience:5","image":"123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/experience@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
    "integration":{"task_definition":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-integration:5","image":"123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/stage/integration@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
  },
  "health_urls":["https://api.stage.example.invalid/health"]
}
JSON
cat > "$rollback" <<'JSON'
{"environment":"stage","services":{"commerce":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-commerce:4","experience":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-experience:4","integration":"arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-stage-integration:4"}}
JSON
bash "$repo_root/scripts/infra/deploy-stage.sh" "$release" "$rollback" >/dev/null
[[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 3 ]]
: > "$AWS_CALL_LOG"
if FAIL_HEALTH=1 bash "$repo_root/scripts/infra/deploy-stage.sh" "$release" "$rollback" >/dev/null 2>&1; then
  echo "expected failed health check" >&2; exit 1
fi
[[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 6 ]]
[[ "$(grep -c 'task-definition/nigson-stage-.*:4' "$AWS_CALL_LOG")" == 3 ]]
: > "$AWS_CALL_LOG"
if FAIL_UPDATE=1 bash "$repo_root/scripts/infra/deploy-stage.sh" "$release" "$rollback" >/dev/null 2>&1; then
  echo "expected ECS update failure" >&2; exit 1
fi
[[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 2 ]]
[[ "$(grep -c 'task-definition/nigson-stage-commerce:4' "$AWS_CALL_LOG")" == 1 ]]
jq '.migration_tasks.commerce.exit_code = 1' "$release" > "$temporary_dir/invalid.json"
: > "$AWS_CALL_LOG"
if bash "$repo_root/scripts/infra/deploy-stage.sh" "$temporary_dir/invalid.json" "$rollback" >/dev/null 2>&1; then
  echo "expected invalid migration receipt rejection" >&2; exit 1
fi
[[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG" || true)" == 0 ]]
echo "Stage deployment guard tests PASS"
