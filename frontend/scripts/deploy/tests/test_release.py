import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('seal',ROOT/'scripts/deploy/seal-release.py')
seal=importlib.util.module_from_spec(spec);spec.loader.exec_module(seal)

class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.site=Path(self.temp.name);(self.site/'remotes/shop').mkdir(parents=True)
        for name,value in {'index.html':'Synthetic test','remoteEntry.json':'{}','remotes/shop/remoteEntry.json':'{}','federation.manifest.json':'{"shop":"/remotes/shop/remoteEntry.json"}'}.items(): (self.site/name).write_text(value)
    def test_seals_compiled_shell_and_remote_files(self):
        result=seal.seal(self.site,'a'*40)
        self.assertEqual(result['source_commit'],'a'*40);self.assertEqual(len(result['files']),4)
    def test_rejects_missing_remote(self):
        (self.site/'remotes/shop/remoteEntry.json').unlink()
        with self.assertRaises(ValueError):seal.seal(self.site,'a'*40)
    def test_rejects_external_remote(self):
        (self.site/'federation.manifest.json').write_text('{"shop":"https://foreign.example/remoteEntry.json"}')
        with self.assertRaises(ValueError):seal.seal(self.site,'a'*40)
    def test_rejects_credentials_and_local_fixtures(self):
        for name in ['.env.production','secret.pem','.npmrc','local-distributor-fixtures.json']:
            p=self.site/name;p.write_text('synthetic')
            with self.subTest(name=name),self.assertRaises(ValueError):seal.seal(self.site,'a'*40)
            p.unlink()
    def test_rejects_symlinks(self):
        (self.site/'link').symlink_to(self.site/'index.html')
        with self.assertRaises(ValueError):seal.seal(self.site,'a'*40)

class BuildConfigurationTests(unittest.TestCase):
    def test_missing_or_invalid_site_key_fails_before_building(self):
        import os
        import sys
        from unittest.mock import patch
        spec=importlib.util.spec_from_file_location('builder',ROOT/'scripts/deploy/build-release.py')
        builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
        for key in ['', 'invalid']:
            with self.subTest(key=key), patch.dict(os.environ,{'NIGSON_GOOGLE_RECAPTCHA_SITE_KEY':key}), patch.object(sys,'argv',['build-release.py','--output','/tmp/not-created-by-test']), patch.object(builder.subprocess,'run') as call:
                with self.assertRaisesRegex(SystemExit,'SITE_KEY'):builder.main()
                call.assert_not_called()
