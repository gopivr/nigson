#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_root="$(mktemp -d)"
trap 'rm -rf "$temporary_root"' EXIT
mkdir -p "$temporary_root/bin"

cat > "$temporary_root/bin/aws" <<'MOCK_AWS'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  "ecr get-login-password"*) echo mock-token ;;
  *"ecr describe-images"*)
    repository=""
    image_tag=""
    previous=""
    for argument in "$@"; do
      [[ "$previous" == "--repository-name" ]] && repository=$argument
      [[ "$previous" == "--image-ids" ]] && image_tag=${argument#imageTag=}
      previous=$argument
    done
    key="${repository//\//_}-${image_tag}"
    state_file="${MOCK_AWS_STATE}/${key}"
    if [[ "${MOCK_AWS_BUILD_MISSING:-0}" == "1" && ! -e "$state_file" ]]; then
      touch "$state_file"
      exit 1
    fi
    printf 'sha256:%064d\n' 1
    ;;
  *"ecr describe-image-scan-findings"*)
    if [[ "$*" == *"--query imageScanStatus.status"* ]]; then
      echo COMPLETE
    else
      printf '{"imageScanFindings":{"findingSeverityCounts":{"CRITICAL":%s,"HIGH":%s}}}\n' "${MOCK_CRITICAL:-0}" "${MOCK_HIGH:-0}"
    fi
    ;;
  *) echo "unexpected mocked AWS command: $*" >&2; exit 9 ;;
esac
MOCK_AWS

cat > "$temporary_root/bin/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1 $2" == "buildx version" ]]; then exit 0; fi
if [[ "$1 $2" == "buildx build" ]]; then
  printf '%s\n' "$*" >> "$MOCK_DOCKER_BUILDS"
  exit 0
fi
if [[ "$1" == "login" ]]; then cat >/dev/null; exit 0; fi
echo "unexpected mocked Docker command: $*" >&2
exit 9
MOCK_DOCKER

chmod +x "$temporary_root/bin/aws" "$temporary_root/bin/docker"

run_case() {
  local phase=$1 expect_ai=$2 expect_build=$3
  local summary="$temporary_root/summary-${phase}.md"
  local build_log="$temporary_root/builds-${phase}.log"
  : > "$build_log"
  mkdir -p "$temporary_root/state-${phase}"
  MOCK_AWS_STATE="$temporary_root/state-${phase}" \
  MOCK_AWS_BUILD_MISSING="$expect_build" \
  MOCK_DOCKER_BUILDS="$build_log" \
  GITHUB_SHA=0123456789abcdef0123456789abcdef01234567 \
  GITHUB_STEP_SUMMARY="$summary" \
  PATH="$temporary_root/bin:$PATH" \
    "$repo_root/scripts/infra/publish-images.sh" stage arm64 "$phase" 123456789012 us-east-1

  if [[ "$expect_ai" == "1" ]]; then
    rg -q 'nigson/stage/ai' "$summary"
  elif rg -q 'nigson/stage/ai' "$summary"; then
    echo "AI repository was included before BP5" >&2
    return 1
  fi

  if [[ "$expect_build" == "1" ]]; then
    expected_builds=5
    [[ "$expect_ai" == "1" ]] && expected_builds=6
    [[ "$(wc -l < "$build_log" | tr -d ' ')" == "$expected_builds" ]]
    rg -q -- '--sbom=true' "$build_log"
    rg -q -- '--provenance=mode=max' "$build_log"
  else
    [[ ! -s "$build_log" ]]
  fi
}

bash -n "$repo_root/scripts/infra/publish-images.sh"
run_case 1 0 0
run_case 5 1 1

mkdir -p "$temporary_root/state-critical"
if MOCK_CRITICAL=1 MOCK_AWS_STATE="$temporary_root/state-critical" GITHUB_SHA=0123456789abcdef0123456789abcdef01234567 GITHUB_STEP_SUMMARY="$temporary_root/summary-critical.md" PATH="$temporary_root/bin:$PATH" "$repo_root/scripts/infra/publish-images.sh" stage arm64 1 123456789012 us-east-1 >/dev/null 2>&1; then
  echo "critical ECR findings must fail publication" >&2
  exit 1
fi

echo "Infrastructure artifact publication tests PASS"
