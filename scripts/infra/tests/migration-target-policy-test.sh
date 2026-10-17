#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_classes="$(mktemp -d)"
trap 'rm -rf "$temporary_classes"' EXIT
javac -d "$temporary_classes" \
  "$repo_root/scripts/java/MigrationTargetPolicy.java" \
  "$repo_root/scripts/java/MigrationTargetPolicyTest.java"
java -cp "$temporary_classes" MigrationTargetPolicyTest
