#!/usr/bin/env python3
"""Validate the repository's OpenAPI contract."""

import sys
from pathlib import Path

import yaml
from openapi_spec_validator import validate_spec


def main() -> int:
    spec_path = Path("openapi.yaml")
    if not spec_path.is_file():
        print(f"OpenAPI specification not found: {spec_path}", file=sys.stderr)
        return 1

    with spec_path.open(encoding="utf-8") as spec_file:
        spec = yaml.safe_load(spec_file)

    validate_spec(spec)
    print(f"OpenAPI contract is valid: {spec_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
