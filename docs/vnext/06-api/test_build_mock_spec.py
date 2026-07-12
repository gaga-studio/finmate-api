#!/usr/bin/env python3
"""Tests for the deterministic Prism mock-spec builder."""

from __future__ import annotations

import unittest
from pathlib import Path
import sys

import yaml
from openapi_spec_validator import validate

API_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(API_DIR))

from build_mock_spec import build_mock_document, count_external_values


class BuildMockSpecTest(unittest.TestCase):
    def test_builds_valid_contract_without_unresolved_external_examples(self) -> None:
        canonical = yaml.safe_load((API_DIR / "openapi.yaml").read_text())
        document, materialized = build_mock_document(API_DIR / "openapi.yaml")

        self.assertEqual(materialized, count_external_values(canonical))
        self.assertEqual(count_external_values(document), 0)
        self.assertEqual(
            document["paths"]["/demo/timeline/advance"]["post"]["operationId"],
            "advanceDemoTimeline",
        )
        self.assertIn("ActiveRoutineBuild", document["components"]["schemas"])
        validate(document)


if __name__ == "__main__":
    unittest.main()
