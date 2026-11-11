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
    def test_enabled_pandago_survives_local_restart(self):
        import tempfile
        from types import SimpleNamespace
        for enabled in (True, False):
            with self.subTest(enabled=enabled), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / '.env').write_text('NIGSON_PANDAGO_ENABLED=' + str(enabled).lower() + '\n')
                with patch.object(local, 'ROOT', root), patch.dict(os.environ, {'NIGSON_PANDAGO_ENABLED': str(enabled).lower()}), patch.object(local.subprocess, 'run', return_value=SimpleNamespace(stdout='')) as docker:
                    local.command('up', '-d', 'integration')
                    args = docker.call_args.args[0]
                    self.assertEqual(str(root / 'docker-compose.pandago.yml') in args, enabled)
                    self.assertIn(str(root / 'docker-compose.yml'), args)

    def test_low_disk_space_prevents_docker_build(self):
        from types import SimpleNamespace
        with patch.object(local.shutil, "disk_usage", return_value=SimpleNamespace(free=267 * 1024**2)), patch.object(local.subprocess, "run") as docker:
            with self.assertRaisesRegex(ValueError, "Insufficient host disk space"):
                local.command("build", "commerce")
            docker.assert_not_called()

    def test_sufficient_disk_space_allows_preflight(self):
        from types import SimpleNamespace
        with patch.object(local.shutil, "disk_usage", return_value=SimpleNamespace(free=10 * 1024**3)):
            local.require_build_space()

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
        self.assertTrue(realm["registrationAllowed"])
        self.assertEqual(realm["loginTheme"], "nigson")
        self.assertFalse(realm["resetPasswordAllowed"])
        self.assertEqual(realm["passwordPolicy"], "length(12)")
        bff = next(client for client in realm["clients"] if client["clientId"] == "nigson-bff")
        self.assertEqual(bff["secret"], "mock-bff-local-confidential-secret-32-bytes")
        self.assertIn("http://localhost:4200/bff/callback", bff["redirectUris"])
        self.assertIn("http://localhost:4200", bff["webOrigins"])
        self.assertEqual(bff["attributes"]["exclude.issuer.from.auth.response"], "true")
        self.assertEqual(bff["attributes"]["exclude.session.state.from.auth.response"], "true")
        registration = next(client for client in realm["clients"] if client["clientId"] == "nigson-registration")
        self.assertTrue(registration["serviceAccountsEnabled"])
        self.assertFalse(registration["standardFlowEnabled"])
        self.assertFalse(registration["directAccessGrantsEnabled"])
        self.assertFalse(any(user.get("serviceAccountClientId") == "nigson-registration" for user in realm["users"]))
        self.assertEqual(len(manifest["subjects"]), 13)
        self.assertEqual(len({user["id"] for user in realm["users"]}), 13)
        fields = ("id", "username", "email", "realmRoles", "firstName", "lastName")
        fixtures = [user for user in realm["users"] if not user.get("serviceAccountClientId")]
        self.assertEqual(manifest["subjects"], [{key: user[key] for key in fields} for user in fixtures])
        self.assertTrue(all(user["email"].endswith("@example.invalid") for user in fixtures))

    def test_local_bff_callback_matches_the_mfe_origin_and_signup_is_gated(self):
        compose = (ROOT / "docker-compose.yml").read_text()
        self.assertIn("NIGSON_BFF_CLIENT_SECRET: ${NIGSON_BFF_CLIENT_SECRET:-mock-bff-local-confidential-secret-32-bytes}", compose)
        self.assertIn("NIGSON_BFF_CALLBACK: ${NIGSON_BFF_CALLBACK:-http://localhost:4200/bff/callback}", compose)
        self.assertIn("NIGSON_REGISTRATION_ENABLED: ${NIGSON_REGISTRATION_ENABLED:-true}", compose)
        self.assertIn("NIGSON_REGISTRATION_CLIENT_ID: ${NIGSON_REGISTRATION_CLIENT_ID:-nigson-registration}", compose)

    def test_nigson_login_theme_is_local_and_selected_by_realm(self):
        theme = ROOT / "infrastructure/docker/keycloak/themes/nigson/login"
        properties = (theme / "theme.properties").read_text()
        messages = (theme / "messages/messages_en.properties").read_text()
        css = (theme / "resources/css/nigson.css").read_text()
        realm = json.loads((ROOT / "infrastructure/docker/keycloak/nigson-local-realm.json").read_text())
        self.assertEqual(realm["loginTheme"], "nigson")
        self.assertIn("parent=keycloak", properties)
        self.assertIn("styles=css/login.css css/nigson.css", properties)
        self.assertIn("loginTitle=Sign in to Nigson", messages)
        self.assertIn("#d71920", css)
        compose = (ROOT / "docker-compose.yml").read_text()
        self.assertIn("./infrastructure/docker/keycloak/themes/nigson:/opt/keycloak/themes/nigson:ro", compose)

    def test_local_keycloak_admin_credentials_are_injected_without_hardcoding_in_setup(self):
        compose = (ROOT / "docker-compose.yml").read_text()
        setup = (ROOT / "scripts/local/dependencies.py").read_text()
        self.assertIn("KC_BOOTSTRAP_ADMIN_USERNAME: ${KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME:-local-bootstrap}", compose)
        self.assertIn("KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD:-mock-local-identity-admin}", compose)
        self.assertIn("./infrastructure/docker/keycloak/themes/nigson:/opt/keycloak/themes/nigson:ro", compose)
        self.assertIn('values.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "local-bootstrap")', setup)
        self.assertIn('values.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "mock-local-identity-admin")', setup)
        self.assertNotIn('"password": "mock-local-identity-admin"}, form=True', setup)
        self.assertIn("existing local Keycloak registration client has user-management access", setup)
        self.assertIn('request("/realms/nigson-local/protocol/openid-connect/token", "POST"', setup)

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
