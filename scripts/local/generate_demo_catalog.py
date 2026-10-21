#!/usr/bin/env python3
"""Embed the published owner OpenAPI operations into the static demo page."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PAGE = ROOT / "docs" / "demo" / "index.html"
OWNERS = ("commerce", "experience", "integration", "ai")


def main() -> None:
    operations = []
    for service in OWNERS:
        contract = json.loads((ROOT / "contracts" / "openapi" / f"{service}.yaml").read_text())
        for path, path_item in contract["paths"].items():
            for method, operation in path_item.items():
                if method not in {"get", "post", "put", "patch", "delete"}:
                    continue
                headers = {
                    parameter.get("name", "").lower(): bool(parameter.get("required"))
                    for parameter in [*path_item.get("parameters", []), *operation.get("parameters", [])]
                    if parameter.get("in") == "header"
                }
                description = " ".join(
                    (operation.get("summary") or operation.get("description") or "").split()
                )[:220]
                operations.append(
                    {
                        "service": service,
                        "method": method.upper(),
                        "path": path,
                        "id": operation.get("operationId", ""),
                        "description": description,
                        "requiredIdempotency": headers.get("idempotency-key", False),
                        "requiredVersion": headers.get("if-match", False),
                        "private": service == "integration" or path.startswith("/internal/"),
                    }
                )

    page = PAGE.read_text()
    begin, end = "    // API_CATALOG_BEGIN", "    // API_CATALOG_END"
    start = page.index(begin) + len(begin)
    finish = page.index(end, start)
    embedded = "\n    const endpoints=" + json.dumps(operations, separators=(",", ":"), ensure_ascii=False) + ";\n    "
    PAGE.write_text(page[:start] + embedded + page[finish:])
    print(f"Embedded {len(operations)} OpenAPI operations in {PAGE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
