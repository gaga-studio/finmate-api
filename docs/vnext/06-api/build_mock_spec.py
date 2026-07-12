#!/usr/bin/env python3
"""Materialize local OpenAPI examples into a Prism-ready specification."""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
from typing import Any

import yaml


def count_external_values(value: Any) -> int:
    if isinstance(value, dict):
        return sum(
            (1 if key == "externalValue" else 0) + count_external_values(child)
            for key, child in value.items()
        )
    if isinstance(value, list):
        return sum(count_external_values(child) for child in value)
    return 0


def materialize_external_examples(
    value: Any, *, source_dir: Path, allowed_root: Path
) -> tuple[Any, int]:
    if isinstance(value, list):
        materialized_items = []
        count = 0
        for child in value:
            materialized, child_count = materialize_external_examples(
                child, source_dir=source_dir, allowed_root=allowed_root
            )
            materialized_items.append(materialized)
            count += child_count
        return materialized_items, count

    if not isinstance(value, dict):
        return value, 0

    external_value = value.get("externalValue")
    if external_value is not None:
        if not isinstance(external_value, str) or "://" in external_value:
            raise ValueError(f"mock examples must use a local JSON path: {external_value}")
        target = (source_dir / external_value).resolve()
        try:
            target.relative_to(allowed_root)
        except ValueError as exc:
            raise ValueError(f"mock example escapes the API directory: {external_value}") from exc
        if target.suffix != ".json":
            raise ValueError(f"mock example must be JSON: {external_value}")
        payload = json.loads(target.read_text(encoding="utf-8"))
        materialized = {
            key: copy.deepcopy(child)
            for key, child in value.items()
            if key != "externalValue"
        }
        if "value" in materialized:
            raise ValueError("OpenAPI Example Object cannot contain value and externalValue")
        materialized["value"] = payload
        return materialized, 1

    materialized_mapping: dict[str, Any] = {}
    count = 0
    for key, child in value.items():
        materialized, child_count = materialize_external_examples(
            child, source_dir=source_dir, allowed_root=allowed_root
        )
        materialized_mapping[key] = materialized
        count += child_count
    return materialized_mapping, count


def build_mock_document(openapi_path: Path) -> tuple[dict[str, Any], int]:
    source = openapi_path.resolve()
    document = yaml.safe_load(source.read_text(encoding="utf-8"))
    materialized, count = materialize_external_examples(
        document,
        source_dir=source.parent,
        allowed_root=source.parent,
    )
    if not isinstance(materialized, dict):
        raise ValueError("OpenAPI document must be an object")
    return materialized, count


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Build a Prism-ready OpenAPI file with inline JSON examples."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=Path(__file__).resolve().parent / "openapi.yaml",
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("refusing to overwrite the canonical OpenAPI document")

    document, count = build_mock_document(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    rendered = yaml.safe_dump(document, sort_keys=False, allow_unicode=True)
    output.write_text(
        "# Generated from openapi.yaml; do not edit.\n" + rendered,
        encoding="utf-8",
    )
    print(f"MOCK_SPEC_OK examples={count} output={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
