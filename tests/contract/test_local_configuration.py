"""Reject production/remote local operations before any side effect."""

import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("dependencies", ROOT / "scripts/local/dependencies.py")
local = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(local)


class LocalIsolationTests(unittest.TestCase):
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
