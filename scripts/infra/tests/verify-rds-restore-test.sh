#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
mkdir -p "$temporary_dir/bin"
cat > "$temporary_dir/bin/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  "sts get-caller-identity"*) echo 123456789012 ;;
  *"rds describe-db-instances"*)
    jq -n --arg id "$RESTORE_ID" --arg kms "$AWS_EXPECTED_KMS_KEY_ARN" --arg sg "$AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID" --arg subnet "$AWS_EXPECTED_DATABASE_SUBNET_GROUP" --arg public "${MOCK_PUBLIC:-false}" '
      {DBInstances:[{DBInstanceIdentifier:$id,DBInstanceStatus:"available",Engine:"postgres",EngineVersion:"18.1",DBName:"nigson",PubliclyAccessible:($public == "true"),StorageEncrypted:true,KmsKeyId:$kms,DBSubnetGroup:{DBSubnetGroupName:$subnet},VpcSecurityGroups:[{Status:"active",VpcSecurityGroupId:$sg}],Endpoint:{Address:"nigson-stage-restore-test.abc.eu-west-1.rds.amazonaws.com",Port:5432}}]}'
    ;;
  *"ec2 describe-security-groups"*"sg-restore-db"*)
    jq -n --arg auditor "$AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID" '{SecurityGroups:[{GroupId:"sg-restore-db",IpPermissionsEgress:[],IpPermissions:[{IpProtocol:"tcp",FromPort:5432,ToPort:5432,UserIdGroupPairs:[{GroupId:$auditor}],IpRanges:[],Ipv6Ranges:[],PrefixListIds:[]}]}]}'
    ;;
  *"ec2 describe-security-groups"*"sg-restore-audit"*)
    echo '{"SecurityGroups":[{"GroupId":"sg-restore-audit","IpPermissions":[]}]}'
    ;;
  *) echo "unexpected aws command: $*" >&2; exit 90 ;;
esac
MOCK
cat > "$temporary_dir/bin/psql" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "$PGSSLMODE" == verify-full && "$PGDATABASE" == nigson ]]
query=$(cat)
[[ "$query" == *"databasechangelog"* ]]
if [[ "${RESTORE_PRODUCT_PHASE:-}" == 5 ]]; then [[ "$query" == *"('ai')"* ]]; fi
printf 't\n'
MOCK
chmod +x "$temporary_dir/bin/"*
printf 'passfile' > "$temporary_dir/pgpass"
printf 'rootcert' > "$temporary_dir/root.crt"
chmod 600 "$temporary_dir/pgpass"
export PATH="$temporary_dir/bin:$PATH"
export AWS_REGION=eu-west-1 AWS_EXPECTED_ACCOUNT_ID=123456789012
export AWS_EXPECTED_KMS_KEY_ARN=arn:aws:kms:eu-west-1:123456789012:key/abc
export AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID=sg-restore-db AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID=sg-restore-audit
export AWS_EXPECTED_DATABASE_SUBNET_GROUP=nigson-stage-commerce RESTORE_AUDIT_DB_USER=nigson_restore_auditor RESTORE_PRODUCT_PHASE=1
export PGPASSFILE="$temporary_dir/pgpass" PGSSLROOTCERT="$temporary_dir/root.crt" RESTORE_ID=nigson-stage-restore-test
bash "$repo_root/scripts/infra/verify-rds-restore.sh" "$RESTORE_ID" >/dev/null
expect_failure() {
  set +e
  "$@" >/dev/null 2>&1
  result=$?
  set -e
  [[ "$result" -ne 0 ]] || { echo "expected command to fail: $*" >&2; exit 1; }
}
expect_failure env MOCK_PUBLIC=true bash "$repo_root/scripts/infra/verify-rds-restore.sh" "$RESTORE_ID"
expect_failure bash "$repo_root/scripts/infra/verify-rds-restore.sh" nigson-stage-commerce
RESTORE_PRODUCT_PHASE=5 bash "$repo_root/scripts/infra/verify-rds-restore.sh" "$RESTORE_ID" >/dev/null
chmod 644 "$PGPASSFILE"
expect_failure bash "$repo_root/scripts/infra/verify-rds-restore.sh" "$RESTORE_ID"
echo "RDS restore verification tests PASS"
