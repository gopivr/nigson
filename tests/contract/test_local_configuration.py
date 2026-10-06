"""Reject production/remote local operations before any side effect."""

import importlib.util
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("dependencies", ROOT / "scripts/local/dependencies.py")
local = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(local)


class LocalIsolationTests(unittest.TestCase):
    def test_down_includes_every_profile_and_retains_volumes(self):
        with patch("sys.argv", ["dependencies.py", "down"]), patch.object(local, "validate", return_value={}), patch.object(local, "command") as docker:
            self.assertEqual(local.main(), 0)
            docker.assert_called_once_with("--profile", "*", "down", "--timeout", "30")

    def test_fixture_manifest_matches_real_identity_import(self):
        manifest = json.loads((ROOT / "tests/fixtures/local-identities.json").read_text())
        realm = json.loads((ROOT / "infrastructure/docker/keycloak/nigson-local-realm.json").read_text())
        self.assertTrue(manifest["localOnly"])
        self.assertEqual(manifest["fixtureClock"], "2026-10-04T00:00:00Z")
        self.assertEqual(manifest["identityRealm"], realm["realm"])
        self.assertEqual(len(manifest["subjects"]), 13)
        self.assertEqual(len({user["id"] for user in realm["users"]}), 13)
        fields = ("id", "username", "email", "realmRoles", "firstName", "lastName")
        self.assertEqual(manifest["subjects"], [{key: user[key] for key in fields} for user in realm["users"]])
        self.assertTrue(all(user["email"].endswith("@example.invalid") for user in realm["users"]))

    def test_remote_docker_host_refused_before_docker(self):
        with patch.dict(os.environ, {"ENV": "local", "DOCKER_HOST": "tcp://production.example.invalid:2376"}), patch.object(local, "command") as docker:
            with self.assertRaises(ValueError):
                local.validate()
            docker.assert_not_called()

    def test_missing_migration_credentials_explained_before_docker(self):
        with patch.dict(os.environ, {"ENV": "local", "COMMERCE_MIGRATION_PASSWORD": ""}), patch.object(local, "command") as docker:
            with self.assertRaisesRegex(ValueError, "COMMERCE_MIGRATION_PASSWORD"):
                local.validate()
            docker.assert_not_called()

    def test_unknown_profile_rejected_before_docker(self):
        with patch.dict(os.environ, {"PROFILE": "production"}), patch("subprocess.run") as docker:
            with self.assertRaises(ValueError):
                local.command("up")
            docker.assert_not_called()

    def test_implicit_provider_profile_rejected_before_docker(self):
        with patch.dict(os.environ, {"ENV": "local", "PROFILES": "unapproved"}), patch.object(local, "command") as docker:
            with self.assertRaises(ValueError):
                local.validate()
            docker.assert_not_called()

    def test_unapproved_fixture_profile_refused_before_identity(self):
        with patch.object(local, "settings", return_value={"FIXTURE_PROFILE": "production"}), patch("urllib.request.build_opener") as http:
            with self.assertRaises(ValueError):
                local.seed({})
            http.assert_not_called()

    def test_missing_fixture_password_refused_before_identity(self):
        with patch.object(local, "settings", return_value={"FIXTURE_PROFILE": "foundation"}), patch("urllib.request.build_opener") as http:
            with self.assertRaises(ValueError):
                local.seed({})
            http.assert_not_called()

    def test_remote_migration_target_rejected(self):
        config = {"services": {"migrate-commerce": {"image": "nigson/migration:local",
            "build": {"context": str(ROOT), "dockerfile": "services/commerce/Dockerfile", "target": "migration"},
            "environment": {"APP_ENV": "local", "MIGRATION_OWNER": "commerce", "MIGRATION_RELEASE": "BP1", "MIGRATION_PASSWORD": "mock-only",
                            "MIGRATION_JDBC_URL": "jdbc:postgresql://production.example.invalid/nigson"}}}}
        with self.assertRaises(ValueError):
            local.validate_config(config)

    def test_explicit_schema_gate_is_validated(self):
        config = {"services": {"migrate-commerce": {"image": "nigson/migration:local",
            "build": {"context": str(ROOT), "dockerfile": "services/commerce/Dockerfile", "target": "migration"},
            "environment": {"APP_ENV": "test", "MIGRATION_OWNER": "commerce", "MIGRATION_PASSWORD": "mock-only",
                            "MIGRATION_JDBC_URL": "jdbc:postgresql://postgres:5432/nigson", "MIGRATION_RELEASE": "BP1"}}}}
        self.assertEqual(local.validate_config(config), config)
        config["services"]["migrate-commerce"]["environment"]["MIGRATION_RELEASE"] = "BP0"
        with self.assertRaisesRegex(ValueError, "deployment gate"):
            local.validate_config(config)

    def test_wrong_owner_role_rejected(self):
        config = {"services": {"commerce": {"image": "nigson/commerce:local",
            "build": {"context": str(ROOT), "dockerfile": "services/commerce/Dockerfile", "target": "application"},
            "environment": {"APP_ENV": "local", "NIGSON_DB_USER": "nigson_experience_app",
                            "NIGSON_JDBC_URL": "jdbc:postgresql://postgres:5432/nigson?currentSchema=commerce"}}}}
        with self.assertRaises(ValueError):
            local.validate_config(config)

    def test_foundation_make_targets_have_real_entrypoints(self):
        content = (ROOT / "Makefile").read_text()
        for action in ("local-up", "local-down", "local-status", "migrate", "seed", "run-core", "run-ai", "cold-start", "command-surface-check"):
            self.assertIn(action + ":", content)
        self.assertIn("export PROFILE ENV", content)

    def test_prod_environment_rejected_before_docker(self):
        with patch.dict(os.environ, {"ENV": "prod"}), patch.object(local, "command") as docker:
            with self.assertRaises(ValueError):
                local.validate()
            docker.assert_not_called()

    def test_nonlocal_project_rejected_before_docker(self):
        with patch.dict(os.environ, {"COMPOSE_PROJECT_NAME": "production"}), patch("subprocess.run") as docker:
            with self.assertRaises(ValueError):
                local.command("down")
            docker.assert_not_called()

    def test_externally_published_port_rejected(self):
        config = {"services": {"postgres": {"image": "postgres@sha256:" + "a" * 64, "environment": {"APP_ENV": "local"},
                  "ports": [{"host_ip": "0.0.0.0", "published": "5432"}]}}}
        with self.assertRaises(ValueError):
            local.validate_config(config)

    def test_prod_configuration_rejected(self):
        config = {"services": {"postgres": {"image": "postgres@sha256:" + "a" * 64, "environment": {"APP_ENV": "prod"}}}}
        with self.assertRaises(ValueError):
            local.validate_config(config)

    def test_floating_image_rejected(self):
        config = {"services": {"postgres": {"image": "postgres:latest", "environment": {"APP_ENV": "local"}}}}
        with self.assertRaises(ValueError):
            local.validate_config(config)


if __name__ == "__main__":
    unittest.main()
