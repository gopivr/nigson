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
docker buildx version >/dev/null
aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry" >/dev/null

publish_and_scan() {
  local repository=$1 dockerfile=$2 target=$3 image_tag=$4
  local image="${registry}/${repository}:${image_tag}"
  local digest scan_status findings critical high
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

  for ((attempt = 0; attempt < 60; attempt++)); do
    scan_status=$(aws ecr describe-image-scan-findings --region "$region" --repository-name "$repository" --image-id "imageTag=${image_tag}" --query 'imageScanStatus.status' --output text 2>/dev/null || true)
    [[ "$scan_status" == "COMPLETE" ]] && break
    if [[ "$scan_status" == "FAILED" || "$scan_status" == "UNSUPPORTED_IMAGE" ]]; then
      echo "ECR vulnerability scan failed for ${repository}:${image_tag}: ${scan_status}" >&2
      return 1
    fi
    sleep 10
  done
  [[ "${scan_status:-}" == "COMPLETE" ]] || { echo "Timed out waiting for ECR scan: ${repository}:${image_tag}" >&2; return 1; }

  findings=$(aws ecr describe-image-scan-findings --region "$region" --repository-name "$repository" --image-id "imageTag=${image_tag}" --output json)
  critical=$(jq '[.imageScanFindings.findingSeverityCounts.CRITICAL // 0] | add' <<<"$findings")
  high=$(jq '[.imageScanFindings.findingSeverityCounts.HIGH // 0] | add' <<<"$findings")
  printf '| `%s` | `%s` | `%s` | %s | %s |\n' "$repository" "$image_tag" "$digest" "$critical" "$high" >> "$summary"
  if (( critical > 0 || high > 0 )); then
    echo "Image ${repository}:${image_tag} has ${critical} critical and ${high} high findings." >&2
    return 1
  fi
}

summary=${GITHUB_STEP_SUMMARY:-/dev/null}
printf '\n| Repository | Immutable tag | Digest | Critical | High |\n|---|---|---|---:|---:|\n' >> "$summary"

publish_and_scan "nigson/${environment}/commerce" services/commerce/Dockerfile application "$tag"
publish_and_scan "nigson/${environment}/commerce" services/commerce/Dockerfile migration "${tag}-migration"
publish_and_scan "nigson/${environment}/experience" services/experience/Dockerfile application "$tag"
publish_and_scan "nigson/${environment}/integration" services/integration/Dockerfile application "$tag"
if [[ "$product_phase" == "5" ]]; then
  publish_and_scan "nigson/${environment}/ai" services/ai/Dockerfile "" "$tag"
fi
publish_and_scan "nigson/${environment}/identity" infrastructure/docker/keycloak/Dockerfile "" "$tag"

printf '\nAll published images target ECS `%s`; set each task definition architecture to `%s` and use the reported immutable digest. Product phase `%s`.\n' "$architecture" "$ecs_architecture" "$product_phase" >> "$summary"
