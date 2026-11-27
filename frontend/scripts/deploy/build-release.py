#!/usr/bin/env python3
"""Build a real production shell and every configured remote into one static artifact."""
import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    target = args.output.resolve()
    site_key = os.environ.get('NIGSON_GOOGLE_RECAPTCHA_SITE_KEY', '').strip()
    if not re.fullmatch(r'[A-Za-z0-9_-]{20,100}', site_key):
        raise SystemExit('NIGSON_GOOGLE_RECAPTCHA_SITE_KEY must be configured for a production release.')
    if target.exists():
        raise SystemExit('Output already exists; choose a new release directory.')
    source_manifest = json.loads((ROOT / 'projects/shell/public/federation.manifest.json').read_text())
    projects = json.loads((ROOT / 'angular.json').read_text())['projects']
    remotes = sorted(source_manifest)
    if not remotes or any(name not in projects or '/' in name or name == 'shell' for name in remotes):
        raise SystemExit('Federation manifest contains unknown remote projects.')
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='nigson-frontend-', dir=target.parent) as temporary:
        staging = Path(temporary)
        site = staging / 'site'
        for name in ['shell', *remotes]:
            output = staging / 'builds' / name
            base = '/' if name == 'shell' else f'/remotes/{name}/'
            subprocess.run(['node', 'node_modules/@angular/cli/bin/ng.js', 'build', name,
                            '--configuration', 'production', '--base-href', base,
                            '--output-path', os.path.relpath(output, ROOT)], cwd=ROOT, check=True)
            browser = output / 'browser'
            if not (browser / 'remoteEntry.json').is_file():
                raise SystemExit(f'Missing federation output for {name}')
            destination = site if name == 'shell' else site / 'remotes' / name
            shutil.copytree(browser, destination, dirs_exist_ok=True)
        (site / 'federation.manifest.json').write_text(json.dumps(
            {name: f'/remotes/{name}/remoteEntry.json' for name in remotes}, indent=2) + '\n')
        index = site / 'index.html'
        html = index.read_text()
        pattern = r'<meta name="google-recaptcha-site-key" content="[^"]*"\s*/?>'
        html, replacements = re.subn(pattern, f'<meta name="google-recaptcha-site-key" content="{site_key}" />', html)
        if replacements != 1:
            raise SystemExit('Shell index must contain exactly one Google reCAPTCHA site-key meta tag.')
        index.write_text(html)
        # Local account fixtures are never deployment assets.
        for fixture in site.rglob('local-distributor-fixtures.json'):
            fixture.unlink()
        if not (site / 'index.html').is_file():
            raise SystemExit('Shell index.html missing')
        site.rename(target)
    print(f'Built production shell and {len(remotes)} remotes: {target}')


if __name__ == '__main__':
    main()
