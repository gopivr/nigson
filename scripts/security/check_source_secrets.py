#!/usr/bin/env python3
"""Reject committed runtime credentials without requiring task/status documents."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PATTERNS = (
    re.compile(rb"^-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", re.MULTILINE),
    re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    re.compile(rb"\bsk_live_[A-Za-z0-9]{16,}\b"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
)


def check(root=ROOT):
    names = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
    errors = []
    for name in filter(None, names):
        path = root / name
        # Deleted tracked paths remain in the index until changes are staged.
        if not path.is_file():
            continue
        relative = Path(name)
        if relative.name == ".env" or (relative.name.startswith(".env.") and relative.name != ".env.example") or relative.suffix in {".key", ".pem", ".tfstate"}:
            errors.append("Sensitive runtime file tracked: " + name)
        if any(pattern.search(path.read_bytes()) for pattern in PATTERNS):
            errors.append("Credential pattern found in tracked file: " + name)
    return errors


if __name__ == "__main__":
    errors = check()
    for error in errors:
        print(error)
    if errors:
        raise SystemExit(1)
    print("PASS: tracked source credential checks")
