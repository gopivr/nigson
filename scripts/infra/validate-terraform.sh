#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"
cloud="${1:-all}"
[[ "$cloud" == aws || "$cloud" == ovh || "$cloud" == all ]] || { echo "Usage: bash scripts/infra/validate-terraform.sh [aws|ovh|all]" >&2; exit 2; }
for selected in aws ovh; do
  [[ "$cloud" == all || "$cloud" == "$selected" ]] || continue
  root="infrastructure/terraform/$selected"
  terraform fmt -check -recursive "$root"
  for directory in "$root"/modules/* "$root"/environments/* "$root"/database; do
    [[ -f "$directory/main.tf" || -f "$directory/versions.tf" ]] || continue
    terraform -chdir="$directory" init -backend=false -input=false
    terraform -chdir="$directory" validate
    if [[ -d "$directory/tests" ]]; then terraform -chdir="$directory" test; fi
  done
 done
