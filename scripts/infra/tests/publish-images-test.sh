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
    [[ "$*" == *"imageDigest=${MOCK_EXPECT_SCAN_DIGEST:-sha256:$(printf '%064d' 1)}"* ]] || { echo 'Scan must target the expected image digest, not an attestation index or tag' >&2; exit 9; }
    echo call >> "$MOCK_AWS_STATE/scan-calls"
    if [[ "${MOCK_SCAN_PENDING_ONCE:-0}" == 1 && ! -e "$MOCK_AWS_STATE/pending-returned" ]]; then
      touch "$MOCK_AWS_STATE/pending-returned"
      echo '{"imageScanStatus":{"status":"IN_PROGRESS"}}'
      exit 0
    fi
    if [[ -n "${MOCK_SCAN_ERROR:-}" ]]; then
      echo "An error occurred (${MOCK_SCAN_ERROR}) when calling DescribeImageScanFindings: private-error-detail" >&2
      exit 1
    fi
    printf '{"imageScanStatus":{"status":"%s"},"imageScanFindings":{"imageScanCompletedAt":%s,"findingSeverityCounts":{"CRITICAL":%s,"HIGH":%s},"findings":[{"name":"CVE-2099-12345","severity":"HIGH","attributes":[{"key":"package_name","value":"synthetic-package"},{"key":"package_version","value":"1.0"}],"private":"must-not-export"}]},"private":"must-not-export"}\n' "${MOCK_SCAN_STATUS:-COMPLETE}" "${MOCK_SCAN_COMPLETED:-1234567890}" "${MOCK_CRITICAL:-0}" "${MOCK_HIGH:-0}"
    ;;
  *) echo "unexpected mocked AWS command: $*" >&2; exit 9 ;;
esac
MOCK_AWS

cat > "$temporary_root/bin/docker" <<'MOCK_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1 $2" == "buildx version" ]]; then exit 0; fi
if [[ "$1 $2 $3" == "buildx imagetools inspect" ]]; then
  [[ "$*" == *"@sha256:"* ]] || { echo 'Manifest must be read by immutable digest' >&2; exit 9; }
  case "${MOCK_MANIFEST_MODE:-single}" in
    single) echo '{"schemaVersion":2,"config":{},"layers":[]}' ;;
    index|missing|ambiguous)
      digest=$(printf 'sha256:%064d' 2)
      duplicate=""
      if [[ "${MOCK_MANIFEST_MODE}" == ambiguous ]]; then
        duplicate=",{\"digest\":\"$digest\",\"platform\":{\"os\":\"linux\",\"architecture\":\"arm64\"}}"
      fi
      arch=arm64
      [[ "${MOCK_MANIFEST_MODE}" != missing ]] || arch=amd64
      printf '{"schemaVersion":2,"manifests":[{"digest":"%s","platform":{"os":"linux","architecture":"%s"}},{"digest":"sha256:attestation","platform":{"os":"unknown","architecture":"unknown"},"annotations":{"vnd.docker.reference.type":"attestation-manifest"}}%s]}\n' "$digest" "$arch" "$duplicate"
      ;;
    invalid) echo '{"unexpected":"manifest"}' ;;
  esac
  exit 0
fi
if [[ "$1 $2" == "buildx build" ]]; then
  printf '%s\n' "$*" >> "$MOCK_DOCKER_BUILDS"
  exit 0
fi
if [[ "$1" == "login" ]]; then cat >/dev/null; exit 0; fi
echo "unexpected mocked Docker command: $*" >&2
exit 9
MOCK_DOCKER

cat > "$temporary_root/bin/sleep" <<'MOCK_SLEEP'
#!/usr/bin/env bash
exit 0
MOCK_SLEEP
chmod +x "$temporary_root/bin/aws" "$temporary_root/bin/docker" "$temporary_root/bin/sleep"

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
  GITHUB_WORKSPACE="$temporary_root/workspace-$phase" \
  GITHUB_STEP_SUMMARY="$summary" \
  PATH="$temporary_root/bin:$PATH" \
    "$repo_root/scripts/infra/publish-images.sh" stage arm64 "$phase" 123456789012 us-east-1

  jq -e --argjson phase "$phase" '.schema_version == 1 and .environment == "stage" and .product_phase == $phase and .architecture == "arm64" and (.components | has("commerce") and has("experience") and has("integration") and has("identity") and has("migration"))' "$temporary_root/workspace-$phase/.build/backend-release/release.json" >/dev/null
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
MOCK_MANIFEST_MODE=index MOCK_EXPECT_SCAN_DIGEST=$(printf 'sha256:%064d' 2) run_case 5 1 1

mkdir -p "$temporary_root/state-critical"
if GITHUB_WORKSPACE="$temporary_root/critical-workspace" MOCK_CRITICAL=1 MOCK_AWS_STATE="$temporary_root/state-critical" GITHUB_SHA=0123456789abcdef0123456789abcdef01234567 GITHUB_STEP_SUMMARY="$temporary_root/summary-critical.md" PATH="$temporary_root/bin:$PATH" "$repo_root/scripts/infra/publish-images.sh" stage arm64 1 123456789012 us-east-1 >/dev/null 2>&1; then
  echo "critical ECR findings must fail publication" >&2
  exit 1
fi

expect_failure() {
  local name=$1 message=$2
  mkdir -p "$temporary_root/$name"
  if GITHUB_WORKSPACE="$temporary_root/workspace-$name" MOCK_AWS_STATE="$temporary_root/$name" GITHUB_SHA=0123456789abcdef0123456789abcdef01234567 \
      GITHUB_STEP_SUMMARY="$temporary_root/$name-summary.md" PATH="$temporary_root/bin:$PATH" \
      "$repo_root/scripts/infra/publish-images.sh" stage arm64 1 123456789012 us-east-1 > "$temporary_root/$name.log" 2>&1; then
    echo "Expected publication failure: $name" >&2; exit 1
  fi
  rg -q "$message" "$temporary_root/$name.log"
  [[ ! -e "$temporary_root/workspace-$name/.build/backend-release/release.json" ]] || { echo 'Failed scans must never publish a deployable cohort' >&2; exit 1; }
  if rg -q 'private-error-detail' "$temporary_root/$name.log"; then echo 'Raw AWS error leaked' >&2; exit 1; fi
}

MOCK_SCAN_ERROR=AccessDeniedException expect_failure denied AccessDeniedException
[[ "$(wc -l < "$temporary_root/denied/scan-calls" | tr -d ' ')" == 1 ]]
MOCK_SCAN_STATUS=UNSUPPORTED_IMAGE expect_failure unsupported UNSUPPORTED_IMAGE
MOCK_SCAN_STATUS=SCAN_ELIGIBILITY_EXPIRED expect_failure expired SCAN_ELIGIBILITY_EXPIRED
MOCK_SCAN_ERROR=ScanNotFoundException expect_failure missing-scan 'last status: ScanNotFoundException'
[[ "$(wc -l < "$temporary_root/missing-scan/scan-calls" | tr -d ' ')" == 60 ]]
MOCK_SCAN_STATUS=ACTIVE MOCK_SCAN_COMPLETED=null expect_failure incomplete-enhanced 'last status: ACTIVE'
MOCK_HIGH=1 expect_failure high '1 high findings'
MOCK_HIGH='"invalid"' expect_failure invalid-counts 'Scanning nigson/stage/commerce'
MOCK_SCAN_PENDING_ONCE=1 run_case 1 0 0
MOCK_MANIFEST_MODE=ambiguous expect_failure ambiguous 'Expected exactly one'
MOCK_MANIFEST_MODE=missing expect_failure missing-platform 'Expected exactly one'
MOCK_MANIFEST_MODE=invalid expect_failure invalid-manifest 'Reusing immutable image'
[[ ! -e "$temporary_root/ambiguous/scan-calls" && ! -e "$temporary_root/missing-platform/scan-calls" ]]
MOCK_SCAN_STATUS=ACTIVE MOCK_SCAN_COMPLETED=1234567890 run_case 1 0 0

# Guard the new column: attestations keep their published index digest while the
# vulnerability gate records the distinct runnable image digest.
rg -q "$(printf 'sha256:%064d' 2)" "$temporary_root/summary-5.md"
report="$temporary_root/critical-workspace/.build/image-scans/nigson_stage_commerce-0123456789abcdef0123456789abcdef01234567-arm64.json"
jq -e '.findings[0].id == "CVE-2099-12345" and .findings[0].packages[0].name == "synthetic-package" and .severityCounts.CRITICAL == 1' "$report" >/dev/null
if rg -q 'must-not-export|"private"' "$report"; then echo 'Report exported non-allowlisted fields' >&2; exit 1; fi

echo "Infrastructure artifact publication tests PASS (single/index scans, retained attestations, IAM errors, bounded polling, enhanced scans and vulnerability failures)"
