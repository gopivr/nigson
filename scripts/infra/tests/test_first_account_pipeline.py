"""Offline orchestration tests; no credentials, network or real Terraform/AWS mutations."""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("pipeline", ROOT / "scripts/infra/first-account-pipeline.py")
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)

ACCOUNT = "123456789012"
NS = ["ns-1.awsdns-01.com", "ns-2.awsdns-02.net", "ns-3.awsdns-03.org", "ns-4.awsdns-04.co.uk"]


def applied_variables(**updates):
    values = {"expected_account_id": ACCOUNT, "region": "eu-west-1", "environment": "stage", "domain_name": "nigson.com", "zone_name": "stage.nigson.com", "zone_id": "Z1234567890", "vpc_cidr": "10.40.0.0/16", "product_phase": 1, "policy_revision": "test-v1", "frontend_release_id": "initial", "log_retention_days": 30, "database_instance_class": "db.t4g.medium", "publish_task_definitions": False, "activate_applications": False, "activate_identity": False, "image_digests": {}, "application_environment": {}, "application_secret_names": {}, "provider_egress": {}, "alert_email": None, "migration_image_digest": None}
    values.update(updates)
    return values


class FakeCommands:
    def __init__(self, *, bucket=True, state=True, zone=False, recognized=True, delegated=True, account=ACCOUNT, changes=(), configuration=None):
        self.bucket, self.state, self.zone, self.recognized = bucket, state, zone, recognized
        self.delegated, self.account, self.changes = delegated, account, changes
        self.configuration = configuration
        self.calls = []

    def aws(self, *args, **kwargs):
        self.calls.append(list(args))
        service, command = args[:2]
        if service == "sts":
            return {"Account": self.account}
        if command == "head-bucket":
            return {} if self.bucket else None
        if command == "head-object":
            return {} if self.state else None
        if command == "list-hosted-zones-by-name":
            return {"HostedZones": [{"Name": "stage.nigson.com.", "Config": {"PrivateZone": False}}] if self.zone else []}
        if command == "get-bucket-tagging":
            return {"TagSet": [{"Key": k, "Value": v} for k, v in {
                "Application": "nigson" if self.recognized else "other", "Environment": "stage", "ManagedBy": "terraform"}.items()]}
        raise AssertionError(args)

    def call(self, args, capture=False):
        self.calls.append(args)
        if args[0] == "dig":
            return "\n".join(NS) if self.delegated else "ns.wrong.example.\n"
        if "show" in args:
            if args[-1] == "-json":
                return json.dumps({"values": {"outputs": {"deployment_configuration": {"value": {"schema_version": 1, "variables": self.configuration}}}}}) if self.configuration else "{}"
            return json.dumps({"resource_changes": list(self.changes)})
        if "output" in args:
            if args[-1] == "squarespace_nameservers":
                return json.dumps(NS)
            return json.dumps({"expected_account_id": ACCOUNT, "region": "eu-west-1", "environment": "stage",
                "domain_name": "nigson.com", "zone_name": "stage.nigson.com", "zone_id": "Z1234567890",
                "state_bucket": "nigson-123456789012-eu-west-1-terraform-state",
                "state_key": "nigson/first-account/stage/terraform.tfstate"})
        return ""


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name) / "ci"

    def execute(self, action, fake):
        subject = pipeline.Pipeline(ACCOUNT, "eu-west-1", action, work=self.work, commands=fake)
        with patch.object(pipeline.Pipeline, "summary"):
            subject.run()
        return fake.calls

    def test_first_run_migrates_bucket_state_before_full_bootstrap_apply(self):
        calls = self.execute("bootstrap", FakeCommands(bucket=False, state=False))
        migration = next(i for i, c in enumerate(calls) if "-migrate-state" in c)
        applies = [i for i, c in enumerate(calls) if "apply" in c]
        self.assertEqual(len(applies), 2)
        self.assertLess(applies[0], migration)
        self.assertLess(migration, applies[1])
        self.assertTrue(any("-target=aws_s3_bucket.state" in c for c in calls))
        backend = (self.work / "bootstrap-backend.hcl").read_text()
        self.assertIn('key = "nigson/bootstrap/stage/terraform.tfstate"', backend)
        self.assertIn('encrypt = true', backend)
        self.assertIn('use_lockfile = true', backend)
        self.assertNotIn('access_key', backend)
        self.assertNotIn('secret_key', backend)

    def test_historical_snapshot_infers_its_fixed_x86_architecture(self):
        legacy=applied_variables()
        state={'values':{'outputs':{'deployment_configuration':{'value':{'schema_version':1,'variables':legacy}}}}}
        resolved=pipeline.deployment_configuration(state,ACCOUNT,'eu-west-1')
        self.assertEqual(resolved['cpu_architecture'],'X86_64')
        self.assertEqual({k:v for k,v in resolved.items() if k not in {'cpu_architecture','key_administrator_principal_arn'}},legacy)

    def test_bootstrap_records_github_identity_for_immutable_subject_trust(self):
        with patch.dict('os.environ', {'GITHUB_REPOSITORY': 'gopivr/nigson',
                                      'GITHUB_REPOSITORY_ID': '12345',
                                      'GITHUB_REPOSITORY_OWNER_ID': '67890'}):
            self.execute('bootstrap', FakeCommands())
        values = json.loads((self.work/'bootstrap/ci.auto.tfvars.json').read_text())
        self.assertEqual(values['github_repository'], 'gopivr/nigson')
        self.assertEqual(values['github_repository_id'], '12345')
        self.assertEqual(values['github_owner_id'], '67890')

    def test_bootstrap_can_reuse_an_existing_github_oidc_provider(self):
        arn = f'arn:aws:iam::{ACCOUNT}:oidc-provider/token.actions.githubusercontent.com'
        with patch.dict('os.environ', {'GITHUB_OIDC_PROVIDER_ARN': arn}):
            self.execute('bootstrap', FakeCommands())
        values = json.loads((self.work/'bootstrap/ci.auto.tfvars.json').read_text())
        self.assertEqual(values['github_oidc_provider_arn'], arn)

    def test_repeat_bootstrap_uses_remote_state_without_target_or_import(self):
        calls = self.execute("bootstrap", FakeCommands())
        self.assertEqual(sum("apply" in c for c in calls), 1)
        self.assertFalse(any("import" in c or "-migrate-state" in c for c in calls))
        self.assertFalse(any(any(a.startswith("-target=") for a in c) for c in calls))

    def test_interrupted_bucket_only_apply_is_recovered_by_import(self):
        calls = self.execute("bootstrap", FakeCommands(state=False))
        imported = next(i for i, c in enumerate(calls) if "import" in c)
        migration = next(i for i, c in enumerate(calls) if "-migrate-state" in c)
        self.assertLess(imported, migration)
        self.assertEqual(sum("apply" in c for c in calls), 1)

    def test_wrong_account_is_refused_before_terraform_or_remote_state_access(self):
        fake = FakeCommands(account="999999999999")
        with self.assertRaisesRegex(ValueError, "different account"):
            self.execute("bootstrap", fake)
        self.assertEqual(fake.calls, [["sts", "get-caller-identity"]])

    def test_unknown_bucket_is_not_adopted(self):
        fake = FakeCommands(state=False, recognized=False)
        with self.assertRaisesRegex(ValueError, "unrecognized"):
            self.execute("bootstrap", fake)
        self.assertFalse(any("apply" in c or "import" in c for c in fake.calls))

    def test_existing_dns_without_state_requires_explicit_owner_state_migration(self):
        fake = FakeCommands(state=False, zone=True)
        with self.assertRaisesRegex(ValueError, "owning local state"):
            self.execute("bootstrap", fake)
        self.assertFalse(any(c[0] == "terraform" for c in fake.calls))

    def test_plan_never_applies_or_creates_bootstrap_resources(self):
        calls = self.execute("plan", FakeCommands())
        self.assertTrue(any("plan" in c for c in calls))
        self.assertFalse(any("apply" in c or "import" in c for c in calls))

    def test_infrastructure_plan_preserves_the_deployed_ui_release(self):
        variables = applied_variables(frontend_release_id="reviewed-ui-release")
        self.execute("plan", FakeCommands(configuration=variables))
        rendered = json.loads((self.work / "platform.tfvars.json").read_text())
        self.assertEqual(rendered["frontend_release_id"], "reviewed-ui-release")

    def test_upgrade_preserves_a_legacy_recorded_frontend_release(self):
        fake = FakeCommands()
        original = fake.call
        def call(args, capture=False):
            if "show" in args and args[-1] == "-json":
                return json.dumps({"values": {"outputs": {"infrastructure": {"value": {"frontend_release": "legacy-ui"}}}}})
            return original(args, capture)
        fake.call = call
        self.execute("plan", fake)
        self.assertEqual(json.loads((self.work / "platform.tfvars.json").read_text())["frontend_release_id"], "legacy-ui")

    def test_plan_requires_an_existing_bootstrap(self):
        with self.assertRaisesRegex(ValueError, "bootstrap action first"):
            self.execute("plan", FakeCommands(bucket=False, state=False))

    def test_apply_waits_for_squarespace_delegation(self):
        fake = FakeCommands(delegated=False)
        with self.assertRaisesRegex(ValueError, "Squarespace"):
            self.execute("apply", fake)
        self.assertFalse(any("apply" in c or "plan" in c for c in fake.calls))

    def test_apply_uses_exact_saved_platform_plan(self):
        calls = self.execute("apply", FakeCommands())
        applies = [c for c in calls if "apply" in c]
        self.assertEqual(len(applies), 1)
        self.assertEqual(applies[0][-1], str(self.work / "platform.tfplan"))
        self.assertFalse(any("-auto-approve" in c for c in calls))

    def test_destructive_or_runtime_plan_never_applies(self):
        for resource_type, actions in [("aws_lb", ["delete", "create"]), ("aws_ecs_task_definition", ["update"]), ("aws_ecs_service", ["create"])]:
            with self.subTest(resource_type=resource_type):
                fake = FakeCommands(changes=[{"address": "module.test.example", "type": resource_type, "change": {"actions": actions}}])
                # Each pipeline needs a new ephemeral checkout directory.
                self.work = Path(self.temp.name) / resource_type
                with self.assertRaises(ValueError):
                    self.execute("apply", fake)
                self.assertFalse(any("apply" in c for c in fake.calls))

    def test_concurrent_state_is_not_force_copied_over(self):
        fake = FakeCommands(state=False)
        original = fake.aws
        heads = 0
        def aws(*args, **kwargs):
            nonlocal heads
            if args[1] == "head-object":
                heads += 1
                if heads > 1:
                    return {}
            return original(*args, **kwargs)
        fake.aws = aws
        with self.assertRaisesRegex(ValueError, "concurrently"):
            self.execute("bootstrap", fake)
        self.assertFalse(any("-migrate-state" in c for c in fake.calls))

    def test_aws_absence_is_distinct_from_forbidden_or_network_failure(self):
        for code, absent in [("404", True), ("NoSuchKey", True), ("403", False), ("AccessDenied", False), ("", False)]:
            message = f"An error occurred ({code}) when calling the HeadBucket operation" if code else "network failure"
            with self.subTest(code=code), patch.object(pipeline.subprocess, "run", return_value=SimpleNamespace(returncode=1, stdout="", stderr=message)):
                if absent:
                    self.assertIsNone(pipeline.Commands().aws("s3api", "head-bucket", missing=True))
                else:
                    with self.assertRaises(RuntimeError):
                        pipeline.Commands().aws("s3api", "head-bucket", missing=True)


if __name__ == "__main__":
    unittest.main()
