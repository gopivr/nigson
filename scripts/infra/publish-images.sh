#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 5 ]]; then
  echo "usage: publish-images.sh <environment> <architecture> <product-phase> <account-id> <region>" >&2
  exit 2
fi

environment=$1
architecture=$2
product_phase=$3
account_id=$4
region=$5
commit_sha=${GITHUB_SHA:-$(git rev-parse HEAD)}

[[ "$environment" == "stage" || "$environment" == "prod" ]]
[[ "$architecture" == "arm64" || "$architecture" == "amd64" ]]
[[ "$product_phase" =~ ^[1-5]$ ]]
[[ "$account_id" =~ ^[0-9]{12}$ ]]
[[ "$region" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]]
[[ "$commit_sha" =~ ^[a-f0-9]{40}$ ]]

case "$architecture" in
  arm64) ecs_architecture=ARM64 ;;
  amd64) ecs_architecture=X86_64 ;;
esac

registry="${account_id}.dkr.ecr.${region}.amazonaws.com"
tag="${commit_sha}-${architecture}"
scan_work=$(mktemp -d)
trap 'rm -rf "$scan_work"' EXIT
docker buildx version >/dev/null
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry" >/dev/null

publish_and_scan() {
  local repository=$1 dockerfile=$2 target=$3 image_tag=$4
  local image="${registry}/${repository}:${image_tag}"
  local digest scan_digest manifest scan_status findings critical high error_code
  local scan_complete=0
  local -a build_arguments

  if digest=$(aws ecr describe-images --region "$region" --repository-name "$repository" --image-ids "imageTag=${image_tag}" --query 'imageDetails[0].imageDigest' --output text 2>/dev/null) && [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
    echo "Reusing immutable image ${repository}:${image_tag} (${digest})" >&2
  else
    build_arguments=(
      --platform "linux/${architecture}"
      --file "$dockerfile"
      --label "org.opencontainers.image.revision=${commit_sha}"
      --label "org.opencontainers.image.source=${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-unknown}"
      --sbom=true
      --provenance=mode=max
      --push
      --tag "$image"
    )
    [[ -n "$target" ]] && build_arguments+=(--target "$target")
    docker buildx build \
      "${build_arguments[@]}" \
      .
    digest=$(aws ecr describe-images --region "$region" --repository-name "$repository" --image-ids "imageTag=${image_tag}" --query 'imageDetails[0].imageDigest' --output text)
    [[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
  fi

  # Attestations add an OCI index. Scan the requested Linux image, not the
  # index or its unknown/unknown attestation manifests. Inspect by immutable
  # digest so the scanned artifact remains bound to the published image.
  manifest=$(docker buildx imagetools inspect --raw "${registry}/${repository}@${digest}")
  if jq -e 'has("manifests")' <<<"$manifest" >/dev/null; then
    scan_digest=$(jq -er --arg arch "$architecture" '
      [.manifests[] | select(.platform.os == "linux" and .platform.architecture == $arch)
       | select((.annotations["vnd.docker.reference.type"] // "") != "attestation-manifest")
       | .digest] | if length == 1 then .[0] else error("Expected exactly one requested Linux platform image") end
    ' <<<"$manifest")
  else
    jq -e '.schemaVersion == 2 and (.config | type == "object") and (.layers | type == "array")' <<<"$manifest" >/dev/null
    scan_digest=$digest
  fi
  [[ "$scan_digest" =~ ^sha256:[a-f0-9]{64}$ ]]
  echo "Scanning ${repository} linux/${architecture} (${scan_digest}); published digest ${digest}" >&2

  for ((attempt = 0; attempt < 60; attempt++)); do
    if findings=$(aws ecr describe-image-scan-findings --region "$region" --repository-name "$repository" \
        --image-id "imageDigest=${scan_digest}" --output json --no-cli-pager 2>"$scan_work/error"); then
      scan_status=$(jq -er '.imageScanStatus.status | select(type == "string")' <<<"$findings")
      case "$scan_status" in
        COMPLETE) scan_complete=1 ;;
        ACTIVE)
          # Enhanced scanning remains ACTIVE; require a completed initial scan.
          if jq -e '.imageScanFindings.imageScanCompletedAt != null' <<<"$findings" >/dev/null; then scan_complete=1; fi
          ;;
        IN_PROGRESS|PENDING) ;;
        *) echo "ECR scan failed for ${repository}@${scan_digest}: ${scan_status}" >&2; return 1 ;;
      esac
      if (( scan_complete == 1 )); then
        jq -e '.imageScanFindings | type == "object"' <<<"$findings" >/dev/null
        break
      fi
    else
      error_code=$(sed -n 's/.*An error occurred (\([A-Za-z0-9]*\)).*/\1/p' "$scan_work/error" | head -1)
      error_code=${error_code:-REQUEST_FAILED}
      scan_status=$error_code
      case "$error_code" in
        ScanNotFoundException|ImageNotFoundException|ServerException|ThrottlingException|TooManyRequestsException) ;;
        *) echo "ECR scan lookup failed for ${repository}@${scan_digest}: ${error_code}" >&2; return 1 ;;
      esac
    fi
    if (( attempt < 59 )); then sleep 10; fi
  done
  (( scan_complete == 1 )) || { echo "Timed out waiting for ECR scan: ${repository}@${scan_digest}; last status: ${scan_status:-UNKNOWN}. Verify registry scan filters include this image." >&2; return 1; }

  # Never interpret malformed counts as a passing vulnerability scan.
  jq -e '[.imageScanFindings.findingSeverityCounts.CRITICAL // 0, .imageScanFindings.findingSeverityCounts.HIGH // 0] | all(.[]; type == "number" and . >= 0 and floor == .)' <<<"$findings" >/dev/null
  critical=$(jq '.imageScanFindings.findingSeverityCounts.CRITICAL // 0' <<<"$findings")
  high=$(jq '.imageScanFindings.findingSeverityCounts.HIGH // 0' <<<"$findings")
  printf '| `%s` | `%s` | `%s` | `%s` | %s | %s |\n' "$repository" "$image_tag" "$digest" "$scan_digest" "$critical" "$high" >> "$summary"
  # Preserve actionable, allowlisted scan details even when the gate fails.
  # Do not publish raw scan responses, credentials, task state or build context.
  local report_dir="${GITHUB_WORKSPACE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}/.build/image-scans"
  local report="${repository//\//_}-${image_tag}.json"
  mkdir -p "$report_dir"
  jq --arg repository "$repository" --arg tag "$image_tag" --arg published "$digest" \
      --arg scanned "$scan_digest" --arg architecture "$architecture" '
    def important: .severity == "CRITICAL" or .severity == "HIGH";
    {repository: $repository, tag: $tag, architecture: $architecture,
     publishedDigest: $published, scannedDigest: $scanned,
     severityCounts: .imageScanFindings.findingSeverityCounts,
     findings: ([.imageScanFindings.findings[]? | select(important) |
       {id: .name, severity: .severity, packages: [
         {name: ([.attributes[]? | select(.key == "package_name") | .value][0]),
          version: ([.attributes[]? | select(.key == "package_version") | .value][0])}]}] +
       [.imageScanFindings.enhancedFindings[]? | select(important) |
        {id: .packageVulnerabilityDetails.vulnerabilityId, severity: .severity,
         fixAvailable: .fixAvailable,
         packages: [.packageVulnerabilityDetails.vulnerablePackages[]? |
           {name, version, fixedInVersion}]}])}
  ' <<<"$findings" > "$report_dir/$report"
  if (( critical > 0 || high > 0 )); then
    echo "Image ${repository}:${image_tag} has ${critical} critical and ${high} high findings. Download the backend-image-scan-findings artifact for CVE/package details." >&2
    return 1
  fi
}

summary=${GITHUB_STEP_SUMMARY:-/dev/null}
printf '\n| Repository | Immutable tag | Published digest | Scanned image digest | Critical | High |\n|---|---|---|---|---:|---:|\n' >> "$summary"

publish_and_scan "nigson/${environment}/commerce" services/commerce/Dockerfile application "$tag"
publish_and_scan "nigson/${environment}/commerce" services/commerce/Dockerfile migration "${tag}-migration"
publish_and_scan "nigson/${environment}/experience" services/experience/Dockerfile application "$tag"
publish_and_scan "nigson/${environment}/integration" services/integration/Dockerfile application "$tag"
if [[ "$product_phase" == "5" ]]; then
  publish_and_scan "nigson/${environment}/ai" services/ai/Dockerfile "" "$tag"
fi
publish_and_scan "nigson/${environment}/identity" infrastructure/docker/keycloak/Dockerfile "" "$tag"

printf '\nAll published images target ECS `%s`; set each task definition architecture to `%s` and use the reported immutable digest. Product phase `%s`.\n' "$architecture" "$ecs_architecture" "$product_phase" >> "$summary"
