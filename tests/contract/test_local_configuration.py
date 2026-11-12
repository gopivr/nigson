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
    def test_subprocess_failures_are_actionable_and_do_not_echo_secrets(self):
        import subprocess
        stopped=subprocess.CalledProcessError(1,['docker','compose','exec','postgres'],stderr='service "postgres" is not running')
        self.assertIn('postgres is not running',local.subprocess_failure_detail(stopped))
        missing=subprocess.CalledProcessError(1,['docker','compose','config'],stderr='required variable SOME_REQUIRED_KEY is missing a value: private-secret-value')
        self.assertEqual(local.subprocess_failure_detail(missing),'Missing required configuration setting: SOME_REQUIRED_KEY')
        unknown=subprocess.CalledProcessError(1,['docker','compose','config'],stderr='private-secret-value')
        self.assertNotIn('private-secret-value',local.subprocess_failure_detail(unknown))

    def test_application_preflight_explains_foundation_only_routes(self):
        with self.assertRaisesRegex(ValueError, 'NIGSON_SECURITY_ENABLED=true.*local-app-config'):
            local.application_preflight({'services': {'experience': {'environment': {'NIGSON_SECURITY_ENABLED': 'false'}}}})

    def test_application_preflight_names_missing_live_settings_without_values(self):
        with self.assertRaisesRegex(ValueError, 'PAYSTACK_SECRET_KEY'):
            local.application_preflight({'services': {'commerce': {'environment': {'NIGSON_SECURITY_ENABLED': 'true', 'NIGSON_PROVIDERS_ENABLED': 'true'}}}})
        local.application_preflight({'services': {}})

    def test_example_allows_the_frontend_admin_login_destination(self):
        settings = dict(line.split('=', 1) for line in (ROOT / '.env.example').read_text().splitlines() if '=' in line and not line.startswith('#'))
        self.assertIn('/admin', settings['NIGSON_BFF_RETURN_PATHS'].split(','))

    def test_run_core_starts_dependencies_before_database_migration(self):
        events=[]
        with patch('sys.argv',['dependencies.py','run-core']), patch.object(local,'validate',return_value={}), patch.object(local,'application_preflight'), patch.object(local,'start_dependencies',side_effect=lambda config:events.append('dependencies')), patch.object(local,'migrate',side_effect=lambda:events.append('migration')), patch.object(local,'ensure_actor_key',side_effect=lambda:events.append('trust')), patch.object(local,'command',side_effect=lambda *args:events.append('build' if 'build' in args else 'apps')):
            self.assertEqual(local.main(),0)
        self.assertEqual(events,['dependencies','migration','build','trust','apps'])

    def test_run_core_recovers_signing_trust_before_starting_apps(self):
        events = []
        with patch('sys.argv', ['dependencies.py', 'run-core']), patch.object(local, 'validate', return_value={}), patch.object(local, 'migrate'), patch.object(local, 'start_dependencies'), patch.object(local, 'wait_identity'), patch.object(local, 'configure_registration'), patch.object(local, 'ensure_actor_key', side_effect=lambda: events.append('recover')), patch.object(local, 'command', side_effect=lambda *args: events.append(args)):
            self.assertEqual(local.main(), 0)
        self.assertEqual(events[0][2], 'build')
        self.assertEqual(events[1], 'recover')
        self.assertEqual(events[2][2], 'up')

    def test_local_up_does_not_start_provider_simulators(self):
        commands = []
        with patch('sys.argv', ['dependencies.py', 'up']), patch.object(local, 'validate', return_value={}), \
                patch.object(local, 'command', side_effect=lambda *args, **kwargs: commands.append(args)), \
                patch.object(local, 'wait_identity'), patch.object(local, 'configure_registration'), \
                patch.object(local, 'bootstrap'), patch.object(local.subprocess, 'run') as execute:
            self.assertEqual(local.main(), 0)
        execute.assert_not_called()
        self.assertFalse(any(any(name in args for name in ('paystack-mock', 'twilio-mock')) for args in commands))

    def test_actor_recovery_runs_only_for_enabled_local_security(self):
        import tempfile
        for enabled in (True, False):
            with self.subTest(enabled=enabled), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / '.env').write_text('NIGSON_SECURITY_ENABLED=' + str(enabled).lower() + '\n')
                with patch.object(local, 'ROOT', root), patch.object(local.subprocess, 'run') as execute:
                    local.ensure_actor_key()
                    self.assertEqual(execute.call_count, 1 if enabled else 0)
                    if enabled:
                        self.assertEqual(execute.call_args.args[0][1], str(root / 'scripts/local/restore_actor_key.py'))

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

    def test_down_does_not_validate_broken_startup_configuration(self):
        with patch("sys.argv", ["dependencies.py", "down"]), patch.object(local, "validate", side_effect=AssertionError("Must not read broken env")), patch.object(local, "shutdown_project") as shutdown:
            self.assertEqual(local.main(), 0)
            shutdown.assert_called_once_with()

    def test_shutdown_selects_all_project_containers_without_deleting_volumes(self):
        from types import SimpleNamespace
        responses = [SimpleNamespace(stdout="app-id db-id"), SimpleNamespace(stdout=""), SimpleNamespace(stdout=""), SimpleNamespace(stdout="network-id"), SimpleNamespace(stdout="")]
        with patch.dict(os.environ, {"ENV": "local", "DOCKER_HOST": "unix:///var/run/docker.sock", "DOCKER_CONTEXT": "default", "COMPOSE_PROJECT_NAME": "nigson-local"}), patch.object(local.subprocess, "run", side_effect=responses) as docker:
            local.shutdown_project()
        commands = [call.args[0] for call in docker.call_args_list]
        self.assertEqual(commands[0], ['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=nigson-local'])
        self.assertEqual(commands[1], ['docker', 'stop', '--time', '30', 'app-id', 'db-id'])
        self.assertEqual(commands[2], ['docker', 'rm', 'app-id', 'db-id'])
        self.assertFalse(any('-v' in cmd or '--volumes' in cmd for cmd in commands))

    def test_shutdown_rejects_nonlocal_targets_before_docker(self):
        defaults={"ENV":"local","DOCKER_HOST":"unix:///var/run/docker.sock","DOCKER_CONTEXT":"default","COMPOSE_PROJECT_NAME":"nigson-local"}
        for override in ({"ENV":"prod"},{"DOCKER_HOST":"tcp://remote:2375"},{"DOCKER_CONTEXT":"production"},{"COMPOSE_PROJECT_NAME":"production"}):
            with self.subTest(override=override), patch.dict(os.environ,{**defaults,**override}), patch.object(local.subprocess,"run") as docker:
                with self.assertRaises(ValueError):
                    local.shutdown_project()
                docker.assert_not_called()

    def test_merge_conflict_reports_line_without_secret_values(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'.env').write_text('APP_ENV=local\n<<<<<<< HEAD\nPRIVATE_KEY=do-not-log\n=======\n>>>>>>> other\n')
            with patch.object(local,'ROOT',root), patch.object(local.subprocess,'run') as docker:
                with self.assertRaisesRegex(ValueError,'merge conflict in .env at line 2') as error:
                    local.command('config')
                self.assertNotIn('do-not-log',str(error.exception))
                docker.assert_not_called()

    def test_fixture_identities_are_test_only_and_not_imported_into_local_realm(self):
        manifest = json.loads((ROOT / "tests/fixtures/local-identities.json").read_text())
        realm = json.loads((ROOT / "infrastructure/docker/keycloak/nigson-local-realm.json").read_text())
        self.assertTrue(manifest["localOnly"])
        self.assertEqual(manifest["fixtureClock"], "2026-10-04T00:00:00Z")
        self.assertEqual(manifest["identityRealm"], realm["realm"])
        self.assertEqual(realm.get("users", []), [])
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
        self.assertEqual(len(manifest["subjects"]), 13)
        self.assertEqual(len({user["id"] for user in manifest["subjects"]}), 13)
        self.assertTrue(all(user["email"].endswith("@example.invalid") for user in manifest["subjects"]))

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
        with patch.dict(os.environ, {"ENV": "test", "COMPOSE_PROJECT_NAME": "nigson-test-unit"}), patch.object(local, "settings", return_value={"FIXTURE_PROFILE": "production"}), patch("urllib.request.build_opener") as http:
            with self.assertRaises(ValueError):
                local.seed_test_identities({"name": "nigson-test-unit"})
            http.assert_not_called()

    def test_missing_fixture_password_refused_before_identity(self):
        with patch.dict(os.environ, {"ENV": "test", "COMPOSE_PROJECT_NAME": "nigson-test-unit"}), patch.object(local, "settings", return_value={"FIXTURE_PROFILE": "foundation"}), patch("urllib.request.build_opener") as http:
            with self.assertRaises(ValueError):
                local.seed_test_identities({"name": "nigson-test-unit"})
            http.assert_not_called()

    def test_local_application_cannot_seed_test_identities(self):
        with patch.dict(os.environ, {"ENV": "local", "COMPOSE_PROJECT_NAME": "nigson-local"}), patch("urllib.request.build_opener") as http:
            with self.assertRaisesRegex(ValueError, "ENV=test"):
                local.seed_test_identities({"name": "nigson-local"})
            http.assert_not_called()

    def test_test_identities_require_an_isolated_test_project(self):
        with patch.dict(os.environ, {"ENV": "test", "COMPOSE_PROJECT_NAME": "nigson-local"}), patch("urllib.request.build_opener") as http:
            with self.assertRaisesRegex(ValueError, "isolated nigson-test"):
                local.seed_test_identities({"name": "nigson-local"})
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
        for action in ("local-up", "local-down", "local-status", "migrate", "test-identities", "run-core", "run-ai", "cold-start", "command-surface-check"):
            self.assertIn(action + ":", content)
        self.assertNotIn("\nseed:", content)
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
