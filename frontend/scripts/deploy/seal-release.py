#!/usr/bin/env python3
"""Seal a credential-free frontend build for transfer between workflow jobs."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def seal(site, commit):
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Use the full UI commit SHA")
    if not (site / "index.html").is_file() or not (site / "remoteEntry.json").is_file():
        raise ValueError("Missing compiled shell")
    manifest = json.loads((site / "federation.manifest.json").read_text())
    if not manifest:
        raise ValueError("Missing frontend remotes")
    for name, path in manifest.items():
        if not re.fullmatch(r"[a-z][a-z0-9-]*", name) or path != f"/remotes/{name}/remoteEntry.json" or not (site / path.lstrip("/")).is_file():
            raise ValueError("Invalid same-origin remote artifact")
    files = {}
    for path in sorted(site.rglob("*")):
        if path.is_symlink():
            raise ValueError("Symlinks are not release assets")
        if not path.is_file():
            continue
        if path.name.startswith('.env') or path.name in {'.npmrc', 'local-distributor-fixtures.json'} or path.suffix.lower() in {'.pem', '.key', '.p12', '.pfx', '.tfstate'}:
            raise ValueError("Credential or local fixture file in release")
        files[path.relative_to(site).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return {"schema_version": 1, "source_commit": commit, "files": files}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', required=True, type=Path)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(seal(args.site, args.source_commit), sort_keys=True) + '\n')


if __name__ == '__main__':
    main()
