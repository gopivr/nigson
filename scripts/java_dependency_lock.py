#!/usr/bin/env python3
"""Capture or verify canonical Maven dependency graphs and resolved artifact hashes."""

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = ["."] + ["shared/common-" + name for name in ("security", "observability", "events", "problem")]
MODULES += ["services/" + name for name in ("commerce", "experience", "integration")]


def snapshot(repository: Path) -> dict:
    graphs = {}
    artifacts = {}

    def walk(node):
        group, artifact, version = (node[key] for key in ("groupId", "artifactId", "version"))
        if "SNAPSHOT" in version or version.startswith("["):
            raise ValueError(f"Non-release dependency: {group}:{artifact}:{version}")
        if group != "com.nigson":
            suffix = node.get("type", "jar")
            classifier = "-" + node["classifier"] if node.get("classifier") else ""
            name = f"{artifact}-{version}{classifier}.{suffix}"
            file = repository / group.replace(".", "/") / artifact / version / name
            if not file.is_file():
                raise ValueError(f"Resolved artifact missing: {group}:{name}")
            coordinate = f"{group}:{artifact}:{suffix}:{node.get('classifier', '')}:{version}"
            artifacts[coordinate] = hashlib.sha256(file.read_bytes()).hexdigest()
        for child in node.get("children", []):
            walk(child)

    for module in MODULES:
        graph = json.loads((ROOT / module / "target/dependencies.json").read_text())
        walk(graph)
        graphs[module] = graph
    return {"format": 1, "graphs": graphs, "artifacts_sha256": artifacts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=ROOT / ".build/m2")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/dependencies/java-lock.json")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = snapshot(args.repository)
    if args.check:
        if result != json.loads(args.output.read_text()):
            raise SystemExit("FAIL: resolved Java dependency graph/artifact hashes differ from lock")
        print("PASS: Java dependency graph and artifact hashes match lock")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
        print(f"Captured {len(result['artifacts_sha256'])} release artifact hashes across {len(MODULES)} modules")


if __name__ == "__main__":
    main()
