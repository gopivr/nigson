#!/usr/bin/env python3
"""Validate FOUND-001 onboarding without claiming runtime verification."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys

TASK_ID = r"[A-Z]+(?:-[A-Z]+)?-\d{3}"
REQUIRED_FILES = (
    "README.md", "AGENTS.md", "BACKEND_IMPLEMENTATION_MASTER.md",
    "IMPLEMENTATION_STATUS.md", "SPECIFICATION_VALIDATION.md", ".gitignore",
    ".env.example", "Makefile", "docs/ARCHITECTURE.md", "docs/DATA_MODEL.md",
    "docs/API_CONTRACT.md", "docs/EVENT_CATALOG.md", "docs/SECURITY.md",
    "docs/AI_ARCHITECTURE.md", "docs/LOCAL_DEVELOPMENT.md",
    "docs/INTEGRATION_MOCKS.md", "docs/RELEASE_CONFIGURATION.md",
    "docs/implementation/README.md", "docs/implementation/repository-paths.txt",
    "docs/adr/ADR-008-confirmed-nigeria-launch-providers.md",
)
SECRET_PATTERNS = (
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    re.compile(rb"\bsk_live_[A-Za-z0-9]{16,}\b"),
    re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
)


def check(root: Path, *, tracked: bool = False) -> list[str]:
    errors: list[str] = []
    for name in REQUIRED_FILES:
        if not (root / name).is_file():
            errors.append(f"Missing required file: {name}")
    if errors:
        return errors
    master = (root / "BACKEND_IMPLEMENTATION_MASTER.md").read_text()
    status = (root / "IMPLEMENTATION_STATUS.md").read_text()
    if "Specification1.1.0" not in master or "Specification:1.1.0" not in status:
        errors.append("Root specification/status must identify version 1.1.0")
    tasks = re.findall(rf"^### ({TASK_ID}) —", master, re.MULTILINE)
    entries = re.findall(rf"^- \[([ x])\] ({TASK_ID}) —", status, re.MULTILINE)
    ids = [task for _, task in entries]
    if len(tasks) != len(set(tasks)) or len(ids) != len(set(ids)):
        errors.append("Duplicate task IDs in master or status")
    if set(tasks) != set(ids):
        errors.append(f"Task registry mismatch: missing={sorted(set(tasks)-set(ids))}, "
                      f"unknown={sorted(set(ids)-set(tasks))}")
    paths = (root / "docs/implementation/repository-paths.txt").read_text().splitlines()
    for name in paths:
        relative = Path(name)
        if relative.is_absolute() or ".." in relative.parts:
            errors.append(f"Unsafe repository manifest path: {name}")
        elif not (root / relative).is_dir():
            errors.append(f"Missing required directory: {name}")
    for name in ("kubernetes", "helm", "platform/api-gateway"):
        if (root / name).exists():
            errors.append(f"Out-of-scope deployment path: {name}")
    try:
        result = subprocess.run(
            ["git", "ls-files", "-z"], cwd=root, check=True,
            capture_output=True, timeout=15,
        )
        files = [name for name in result.stdout.decode().split("\0") if name]
        if tracked:
            missing = set(REQUIRED_FILES) - set(files)
            if missing:
                errors.append(f"Required files not tracked: {sorted(missing)}")
            for name in paths:
                if not any(file.startswith(name + "/") for file in files):
                    errors.append(f"Required directory absent from checkout: {name}")
        for name in files:
            relative = Path(name)
            if relative.name == ".env" or (
                relative.name.startswith(".env.") and relative.name != ".env.example"
            ) or relative.suffix in {".key", ".pem", ".tfstate"}:
                errors.append(f"Sensitive runtime file tracked: {name}")
            file = root / name
            if file.is_file() and any(pattern.search(file.read_bytes())
                                      for pattern in SECRET_PATTERNS):
                errors.append(f"Credential pattern found in tracked file: {name}")
    except (subprocess.SubprocessError, OSError) as exc:
        errors.append(f"Cannot inspect Git tracking: {type(exc).__name__}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--tracked", action="store_true", help="Require fresh-checkout paths")
    args = parser.parse_args()
    errors = check(args.root.resolve(), tracked=args.tracked)
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("PASS: TEST-FOUND-TREE — onboarding paths, specification/task registry, "
          "scope and tracked credential-pattern checks; no runtime checks implied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
