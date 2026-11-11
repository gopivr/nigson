"""Local restart recovery must preserve valid keys and never rotate on transport failures."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('actor_recovery', ROOT/'scripts/local/restore_actor_key.py')
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)


class ActorRecoveryTests(unittest.TestCase):
    def run_recovery(self, missing=False, transport_error=False):
        calls = []
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            before = 'APP_ENV=local\nNIGSON_ACTOR_KMS_KEY=old-local-key\nNIGSON_ACTOR_PUBLIC_KEY=AQID\n'
            (root/'.env').write_text(before)
            def execute(args, **kwargs):
                calls.append(args)
                if args[:3] == ['docker', 'context', 'inspect']:
                    return SimpleNamespace(stdout=json.dumps('unix:///synthetic/docker.sock'))
                if 'get-public-key' in args and args[-1] == 'old-local-key' and (missing or transport_error):
                    raise subprocess.CalledProcessError(1, args, stderr='NotFoundException' if missing else 'Connection refused')
                value = {'KeyMetadata': {'Arn': 'new-local-key'}} if 'create-key' in args else {'PublicKey': 'AQID'}
                return SimpleNamespace(stdout=json.dumps(value))
            with patch.object(recovery, 'ROOT', root), patch.dict(os.environ, {'ENV':'local', 'COMPOSE_PROJECT_NAME':'nigson-local', 'DOCKER_HOST':'unix:///synthetic/docker.sock'}), patch.object(recovery.subprocess, 'run', side_effect=execute), patch('builtins.print'):
                if transport_error:
                    with self.assertRaises(subprocess.CalledProcessError):recovery.main()
                else:recovery.main()
            after = (root/'.env').read_text()
            for args in calls[1:]:
                self.assertEqual(args[:5], ['docker','compose','--project-name','nigson-local','--env-file'])
            return before, after, calls

    def test_valid_key_is_preserved(self):
        before, after, calls = self.run_recovery()
        self.assertEqual(before, after)
        self.assertFalse(any('create-key' in args for args in calls))

    def test_missing_key_recovers_all_local_trust_settings(self):
        _, after, calls = self.run_recovery(missing=True)
        self.assertIn('NIGSON_ACTOR_KMS_KEY=new-local-key', after)
        self.assertIn('NIGSON_ACTOR_PUBLIC_KEYS=', after)
        self.assertIn('NIGSON_ACTOR_KEY_ID=local-', after)
        self.assertEqual(sum('create-key' in args for args in calls), 1)

    def test_transport_failure_does_not_rotate_or_edit_config(self):
        before, after, calls = self.run_recovery(transport_error=True)
        self.assertEqual(before, after)
        self.assertFalse(any('create-key' in args for args in calls))
