"""Non-secret configuration handoff; no AWS credentials, APIs or Terraform execution."""
import importlib.util
import unittest
from pathlib import Path
spec = importlib.util.spec_from_file_location("first_account", Path(__file__).resolve().parents[1] / "prepare-first-account.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class FirstAccountInputs(unittest.TestCase):
    def config(self):
        return {"expected_account_id":"123456789012","region":"eu-west-1","environment":"stage","domain_name":"nigson.com","zone_name":"stage.nigson.com","zone_id":"Z123EXAMPLE","state_bucket":"nigson-123456789012-eu-west-1-terraform-state","state_key":"nigson/first-account/stage/terraform.tfstate"}

    def test_handoff_contains_no_credentials_and_enables_state_locking(self):
        inputs, backend = module.render(self.config())
        self.assertEqual(inputs["zone_name"], "stage.nigson.com")
        self.assertNotIn("state_bucket", inputs)
        self.assertIn('use_lockfile = true', backend)
        self.assertIn('encrypt = true', backend)
        self.assertNotIn("secret", backend.lower())

    def test_cross_environment_account_or_zone_mix_is_refused(self):
        for key, value in [("state_key","nigson/first-account/prod/terraform.tfstate"),("state_bucket","another-account-bucket"),("zone_name","nigson.com"),("zone_id","bad-zone")]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                module.render({**self.config(),key:value})

    def test_credential_fields_and_injected_backend_values_are_refused(self):
        with self.assertRaises(ValueError):
            module.render({**self.config(),"aws_secret_access_key":"not-a-real-secret"})
        with self.assertRaises(ValueError):
            module.render({**self.config(),"region":'eu-west-1"\nprofile="unexpected'})

    def test_published_migration_image_is_distinct_from_application_target(self):
        from unittest.mock import Mock
        def response(command, **kwargs):
            if command[1] == "sts": return "123456789012\n"
            return "sha256:" + ("b" * 64 if command[command.index("--image-ids")+1].endswith("-migration") else "a" * 64)
        run = Mock(side_effect=response)
        images = module.capture_images(self.config(), "a" * 40 + "-amd64", run)
        self.assertNotEqual(images["migration_image_digest"], images["image_digests"]["commerce"])
        self.assertNotIn("activate_applications", images)
        self.assertEqual(run.call_count, 6)

    def test_image_capture_checks_account_before_any_ecr_lookup(self):
        from unittest.mock import Mock
        run = Mock(return_value="999999999999")
        with self.assertRaises(ValueError): module.capture_images(self.config(), "a" * 40 + "-amd64", run)
        self.assertEqual(run.call_count, 1)
        with self.assertRaises(ValueError): module.capture_images(self.config(), "a" * 40 + "-arm64", run)

if __name__ == "__main__":
    unittest.main()
