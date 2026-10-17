#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ ^nigson-stage-restore-[a-z0-9-]{1,36}$ ]]; then
  echo "usage: verify-rds-restore.sh <nigson-stage-restore-unique-id>" >&2
  exit 2
fi
restore_id=$1
[[ -n "${AWS_REGION:-}" && -n "${AWS_EXPECTED_ACCOUNT_ID:-}" && "$AWS_EXPECTED_ACCOUNT_ID" =~ ^[0-9]{12}$ ]] || { echo "stage AWS account configuration is required" >&2; exit 1; }
[[ -n "${AWS_EXPECTED_KMS_KEY_ARN:-}" && -n "${AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID:-}" ]] || { echo "expected restore KMS and security group are required" >&2; exit 1; }
[[ -n "${AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID:-}" ]] || { echo "isolated restore auditor security group is required" >&2; exit 1; }
[[ "${AWS_EXPECTED_DATABASE_SUBNET_GROUP:-}" == "nigson-stage-commerce" ]] || { echo "expected stage database subnet group is required" >&2; exit 1; }
[[ -n "${RESTORE_AUDIT_DB_USER:-}" && "$RESTORE_AUDIT_DB_USER" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || { echo "a named read-only restore auditor login is required" >&2; exit 1; }
[[ "${RESTORE_PRODUCT_PHASE:-}" =~ ^[1-5]$ ]] || { echo "explicit restored product phase is required" >&2; exit 1; }
[[ -f "${PGPASSFILE:-}" && -f "${PGSSLROOTCERT:-}" ]] || { echo "protected PostgreSQL passfile and CA certificate are required" >&2; exit 1; }
passfile_mode=$(stat -f '%Lp' "$PGPASSFILE" 2>/dev/null || stat -c '%a' "$PGPASSFILE") || { echo "cannot inspect PostgreSQL passfile permissions" >&2; exit 1; }
[[ "$passfile_mode" == "600" ]] || { echo "PostgreSQL passfile permissions must be 600" >&2; exit 1; }
for command in aws jq psql; do command -v "$command" >/dev/null || { echo "required command unavailable: $command" >&2; exit 1; }; done

account=$(aws sts get-caller-identity --query Account --output text --no-cli-pager) || { echo "could not verify AWS account" >&2; exit 1; }
[[ "$account" == "$AWS_EXPECTED_ACCOUNT_ID" ]] || { echo "AWS account does not match the expected stage account" >&2; exit 1; }
description=$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$restore_id" --no-cli-pager --output json) || { echo "could not inspect restored RDS instance" >&2; exit 1; }
instance=$(jq -e --arg id "$restore_id" --arg kms "$AWS_EXPECTED_KMS_KEY_ARN" \
  --arg sg "$AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID" --arg subnet "$AWS_EXPECTED_DATABASE_SUBNET_GROUP" '
  .DBInstances | select(length == 1) | .[0] |
  select(.DBInstanceIdentifier == $id and .DBInstanceStatus == "available" and
    .Engine == "postgres" and (.EngineVersion | startswith("18.")) and
    .DBName == "nigson" and .PubliclyAccessible == false and
    .StorageEncrypted == true and .KmsKeyId == $kms and
    .DBSubnetGroup.DBSubnetGroupName == $subnet and
    ([.VpcSecurityGroups[] | select(.Status == "active") | .VpcSecurityGroupId] == [$sg]) and
    (.Endpoint.Address | type == "string" and test("^(?:[a-z0-9-]+\\.)+[a-z0-9-]+\\.rds\\.amazonaws\\.com$")) and
    .Endpoint.Port == 5432)
' <<<"$description") || { echo "restored RDS target failed isolation, encryption, engine, or availability checks" >&2; exit 1; }
endpoint=$(jq -er '.Endpoint.Address' <<<"$instance")
port=$(jq -er '.Endpoint.Port' <<<"$instance")
[[ "$endpoint" == *".$AWS_REGION.rds.amazonaws.com" ]] || { echo "restored RDS endpoint does not match the expected region" >&2; exit 1; }

restore_sg=$(aws ec2 describe-security-groups --region "$AWS_REGION" --group-ids "$AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID" --no-cli-pager --output json) || { echo "could not inspect restored database security group" >&2; exit 1; }
jq -e --arg db "$AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID" --arg auditor "$AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID" '
  .SecurityGroups | select(length == 1) | .[0] |
  select(.GroupId == $db and (.IpPermissionsEgress | length == 0) and
    (.IpPermissions | length == 1) and
    .IpPermissions[0].IpProtocol == "tcp" and
    .IpPermissions[0].FromPort == 5432 and .IpPermissions[0].ToPort == 5432 and
    ([.IpPermissions[0].UserIdGroupPairs[] | .GroupId] == [$auditor]) and
    ((.IpPermissions[0].IpRanges // []) | length == 0) and
    ((.IpPermissions[0].Ipv6Ranges // []) | length == 0) and
    ((.IpPermissions[0].PrefixListIds // []) | length == 0))
' <<<"$restore_sg" >/dev/null || { echo "restored database security group is not isolated to the audit runner" >&2; exit 1; }
auditor_sg=$(aws ec2 describe-security-groups --region "$AWS_REGION" --group-ids "$AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID" --no-cli-pager --output json) || { echo "could not inspect restore auditor security group" >&2; exit 1; }
jq -e --arg auditor "$AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID" '
  .SecurityGroups | select(length == 1) | .[0] |
  select(.GroupId == $auditor and (.IpPermissions | length == 0))
' <<<"$auditor_sg" >/dev/null || { echo "restore auditor security group must have no ingress" >&2; exit 1; }

# The caller supplies a separately provisioned least-privilege audit login via a protected passfile.
export PGHOST="$endpoint" PGPORT="$port" PGDATABASE=nigson PGUSER="$RESTORE_AUDIT_DB_USER"
export PGSSLMODE=verify-full PGSSLROOTCERT PGPASSFILE
required_owners="('commerce'), ('experience'), ('integration')"
if [[ "$RESTORE_PRODUCT_PHASE" == 5 ]]; then required_owners+=", ('ai')"; fi
psql --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align <<SQL | while IFS= read -r passed; do [[ "$passed" == t ]] || exit 1; done || { echo "restored database owner schemas or Liquibase history failed verification" >&2; exit 1; }
WITH required(owner) AS (VALUES $required_owners),
checks AS (
  SELECT owner,
    EXISTS (SELECT 1 FROM pg_namespace n WHERE n.nspname = owner AND pg_get_userbyid(n.nspowner) = 'nigson_' || owner || '_migrator')
    AND to_regclass(owner || '.databasechangelog') IS NOT NULL AS valid
  FROM required
)
SELECT bool_and(valid) FROM checks;
SQL

printf 'RDS restore verification PASS: target=%s engine=postgres18 private=1 encrypted=1 product_phase=%s tls=verify-full\n' "$restore_id" "$RESTORE_PRODUCT_PHASE"
