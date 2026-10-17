#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_root="$(mktemp -d)"
trap 'rm -rf "$temporary_root"' EXIT
mkdir -p "$temporary_root/bin"

cat > "$temporary_root/bin/terraform" <<'MOCK_TERRAFORM'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == -chdir=infrastructure/terraform/environments/stage ]]
[[ "$2" == output && "$3" == -json && "$4" == migrations ]]
cat <<'JSON'
{"environment":"stage","region":"eu-west-1","task_definition_arns":{"commerce":"arn:aws:ecs:eu-west-1:123456789012:task-definition/commerce:1","experience":"arn:aws:ecs:eu-west-1:123456789012:task-definition/experience:1","integration":"arn:aws:ecs:eu-west-1:123456789012:task-definition/integration:1"},"cluster_arn":"arn:aws:ecs:eu-west-1:123456789012:cluster/nigson-stage","private_subnet_ids":["subnet-private-a","subnet-private-b"],"security_group_id":"sg-migration"}
JSON
MOCK_TERRAFORM

cat > "$temporary_root/bin/aws" <<'MOCK_AWS'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  "sts get-caller-identity"*) echo 123456789012 ;;
  *"ecs run-task"*)
    echo launch >> "$MOCK_RUN_LOG"
    network_file=""
    previous=""
    for argument in "$@"; do
      [[ "$previous" == "--network-configuration" ]] && network_file=${argument#file://}
      previous=$argument
    done
    [[ -s "$network_file" ]]
    jq -e '.awsvpcConfiguration.assignPublicIp == "DISABLED" and (.awsvpcConfiguration.subnets | length) == 2' "$network_file" >/dev/null
    printf '{"tasks":[{"taskArn":"arn:aws:ecs:eu-west-1:123456789012:task/nigson-stage/abc123"}],"failures":[]}\n'
    ;;
  *"ecs wait tasks-stopped"*) [[ "${MOCK_WAIT_FAIL:-0}" == "0" ]] ;;
  *"ecs describe-tasks"*) printf '{"tasks":[{"containers":[{"name":"owner-migration","exitCode":%s}]}]}\n' "${MOCK_EXIT_CODE:-0}" ;;
  *) echo "unexpected mocked AWS command: $*" >&2; exit 9 ;;
esac
MOCK_AWS

chmod +x "$temporary_root/bin/terraform" "$temporary_root/bin/aws"
export AWS_REGION=eu-west-1 AWS_EXPECTED_ACCOUNT_ID=123456789012
export PATH="$temporary_root/bin:$PATH"
export MOCK_RUN_LOG="$temporary_root/run-count"

"$repo_root/scripts/infra/run-owner-migration.sh" stage commerce >/dev/null
[[ "$(wc -l < "$MOCK_RUN_LOG" | tr -d ' ')" == 1 ]]

if MOCK_EXIT_CODE=1 "$repo_root/scripts/infra/run-owner-migration.sh" stage commerce >/dev/null 2>&1; then
  echo "failed migration task must fail the launcher" >&2
  exit 1
fi
[[ "$(wc -l < "$MOCK_RUN_LOG" | tr -d ' ')" == 2 ]]

if MOCK_WAIT_FAIL=1 "$repo_root/scripts/infra/run-owner-migration.sh" stage commerce >/dev/null 2>&1; then
  echo "unconfirmed migration state must fail the launcher" >&2
  exit 1
fi
[[ "$(wc -l < "$MOCK_RUN_LOG" | tr -d ' ')" == 3 ]]

echo "Owner migration launch tests PASS"
