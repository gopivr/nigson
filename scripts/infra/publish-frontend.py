#!/usr/bin/env python3
"""Upload a reviewed frontend artifact to its immutable AWS release prefix."""
import argparse
import json
import mimetypes
import re
import subprocess
from pathlib import Path


def validate(site):
    manifest = json.loads((site / 'federation.manifest.json').read_text())
    if not manifest or not (site / 'index.html').is_file() or not (site / 'remoteEntry.json').is_file():
        raise ValueError('Incomplete shell artifact')
    for name, url in manifest.items():
        if not re.fullmatch(r'[a-z][a-z0-9-]*', name) or url != f'/remotes/{name}/remoteEntry.json':
            raise ValueError('Remote entries must use same-origin deployment URLs')
        entry = site / url.lstrip('/')
        if not entry.is_file():
            raise ValueError(f'Missing remote: {name}')
    for path in site.rglob('*'):
        if path.is_symlink():
            raise ValueError('Symlinks are not deployment assets')
    for directory in [site, *(site / 'remotes' / name for name in manifest)]:
        entry = json.loads((directory / 'remoteEntry.json').read_text())
        for record in entry.get('shared', []) + entry.get('exposes', []):
            filename = record.get('outFileName')
            if not filename or Path(filename).is_absolute() or '..' in Path(filename).parts or not (directory / filename).is_file():
                raise ValueError('Federation entry references a missing or invalid bundle')
    files = sorted(path for path in site.rglob('*') if path.is_file())
    for path in files:
        if path.is_symlink() or path.suffix.lower() in {'.pem', '.key', '.tfstate'} or path.name.startswith('.env') or path.name == 'local-distributor-fixtures.json':
            raise ValueError('Artifact contains a symlink, credential file or local fixture')
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--site', required=True, type=Path)
    parser.add_argument('--bucket', required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--validate-only', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]', args.bucket) or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}', args.release):
        raise SystemExit('Invalid bucket or release identifier')
    site = args.site.resolve()
    files = validate(site)
    if args.validate_only:
        print(f'Valid frontend release: {len(files)} files')
        return
    # Never overwrite an existing release, including an interrupted upload.
    prefix = f'releases/{args.release}/'
    probe = subprocess.run(['aws', 's3api', 'list-objects-v2', '--bucket', args.bucket, '--prefix', prefix,
                            '--max-keys', '1', '--output', 'json'], check=True, text=True, capture_output=True)
    if json.loads(probe.stdout).get('KeyCount', 0):
        raise SystemExit('Release prefix already exists; choose a new release ID')
    # Metadata remains revalidated; no private/API data is uploaded or cached.
    for path in files:
        relative = path.relative_to(site).as_posix()
        content_type = 'application/javascript' if path.suffix in {'.js', '.mjs'} else (mimetypes.guess_type(path.name)[0] or 'application/octet-stream')
        subprocess.run(['aws', 's3', 'cp', str(path), f's3://{args.bucket}/{prefix}{relative}',
                        '--content-type', content_type, '--cache-control', 'no-cache', '--only-show-errors'], check=True)
    print(f'Uploaded {len(files)} files to {prefix}. Activate this release_id with a reviewed Terraform plan.')


if __name__ == '__main__':
    main()
