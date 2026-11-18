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
    service="${service#nigson-${TARGET_ENVIRONMENT}-}"
    service="${service%%:*}"
    image="${IMAGE_PREFIX}${service}@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    jq -n --arg image "$image" --arg service "$service" '{taskDefinition:{containerDefinitions:[{name:$service,image:$image}]}}'
    ;;
  "ecs describe-services"*) echo '{"failures":[],"services":[{}]}' ;;
  "ecs update-service"*)
    if [[ "${FAIL_UPDATE:-0}" == 1 && "$*" == *"task-definition/nigson-${TARGET_ENVIRONMENT}-commerce:5"* ]]; then exit 42; fi
    echo '{}'
    ;;
  "ecs wait services-stable"*) [[ "${FAIL_STABLE:-0}" != 1 ]] ;;
  *) echo "unexpected aws command: $*" >&2; exit 90 ;;
esac
MOCK
cat > "$temporary_dir/bin/curl" <<'MOCK'
#!/usr/bin/env bash
[[ "${FAIL_HEALTH:-0}" != 1 ]] || exit 22
printf 200
MOCK
chmod +x "$temporary_dir/bin/"*
export PATH="$temporary_dir/bin:$PATH"
export AWS_REGION=eu-west-1 AWS_EXPECTED_ACCOUNT_ID=123456789012 AWS_CALL_LOG="$temporary_dir/aws.log"

write_manifests() {
  local environment=$1
  export TARGET_ENVIRONMENT=$environment
  export IMAGE_PREFIX="123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/${environment}/"
  jq -n --arg environment "$environment" '{
    environment:$environment,release_id:"test-release",product_phase:1,
    cluster_arn:("arn:aws:ecs:eu-west-1:123456789012:cluster/nigson-" + $environment),
    owners:["commerce","experience","integration"],
    migration_tasks:{
      commerce:{task_arn:("arn:aws:ecs:eu-west-1:123456789012:task/nigson-" + $environment + "/abcdef0123456789"),exit_code:0},
      experience:{task_arn:("arn:aws:ecs:eu-west-1:123456789012:task/nigson-" + $environment + "/abcdef0123456790"),exit_code:0},
      integration:{task_arn:("arn:aws:ecs:eu-west-1:123456789012:task/nigson-" + $environment + "/abcdef0123456791"),exit_code:0}
    },
    services:{
      commerce:{task_definition:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-commerce:5"),image:("123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/" + $environment + "/commerce@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")},
      experience:{task_definition:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-experience:5"),image:("123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/" + $environment + "/experience@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")},
      integration:{task_definition:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-integration:5"),image:("123456789012.dkr.ecr.eu-west-1.amazonaws.com/nigson/" + $environment + "/integration@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")}
    },
    health_urls:["https://api." + $environment + ".example.invalid/health"]
  }' > "$temporary_dir/release-${environment}.json"
  jq -n --arg environment "$environment" '{environment:$environment,services:{
    commerce:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-commerce:4"),
    experience:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-experience:4"),
    integration:("arn:aws:ecs:eu-west-1:123456789012:task-definition/nigson-" + $environment + "-integration:4")}}' > "$temporary_dir/rollback-${environment}.json"
}

for environment in stage prod; do
  release="$temporary_dir/release-${environment}.json"
  rollback="$temporary_dir/rollback-${environment}.json"
  write_manifests "$environment"
  : > "$AWS_CALL_LOG"
  bash "$repo_root/scripts/infra/deploy-environment.sh" "$environment" "$release" "$rollback" >/dev/null
  [[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 3 ]]

  : > "$AWS_CALL_LOG"
  if FAIL_HEALTH=1 bash "$repo_root/scripts/infra/deploy-environment.sh" "$environment" "$release" "$rollback" >/dev/null 2>&1; then
    echo "expected failed health check for $environment" >&2; exit 1
  fi
  [[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 6 ]]
  [[ "$(grep -c "task-definition/nigson-${environment}-.*:4" "$AWS_CALL_LOG")" == 3 ]]

  : > "$AWS_CALL_LOG"
  if FAIL_UPDATE=1 bash "$repo_root/scripts/infra/deploy-environment.sh" "$environment" "$release" "$rollback" >/dev/null 2>&1; then
    echo "expected ECS update failure for $environment" >&2; exit 1
  fi
  [[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG")" == 2 ]]
  [[ "$(grep -c "task-definition/nigson-${environment}-commerce:4" "$AWS_CALL_LOG")" == 1 ]]

  jq '.migration_tasks.commerce.exit_code = 1' "$release" > "$temporary_dir/invalid-${environment}.json"
  : > "$AWS_CALL_LOG"
  if bash "$repo_root/scripts/infra/deploy-environment.sh" "$environment" "$temporary_dir/invalid-${environment}.json" "$rollback" >/dev/null 2>&1; then
    echo "expected invalid migration receipt rejection for $environment" >&2; exit 1
  fi
  [[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG" || true)" == 0 ]]

  other=stage
  [[ "$environment" == stage ]] && other=prod
  : > "$AWS_CALL_LOG"
  if bash "$repo_root/scripts/infra/deploy-environment.sh" "$environment" "$temporary_dir/release-${other}.json" "$rollback" >/dev/null 2>&1; then
    echo "expected cross-environment release rejection for $environment" >&2; exit 1
  fi
  [[ "$(grep -c 'ecs update-service' "$AWS_CALL_LOG" || true)" == 0 ]]
done

if bash "$repo_root/scripts/infra/deploy-environment.sh" development "$temporary_dir/release-stage.json" "$temporary_dir/rollback-stage.json" >/dev/null 2>&1; then
  echo "expected unsupported environment rejection" >&2; exit 1
fi
echo "Stage and production deployment guard tests PASS"
