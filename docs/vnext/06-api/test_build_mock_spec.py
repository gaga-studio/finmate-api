#!/usr/bin/env python3
"""Tests for the deterministic Prism mock-spec builder."""

from __future__ import annotations

import json
import unittest
from pathlib import Path

import yaml
from openapi_spec_validator import validate

from build_mock_spec import build_mock_document, count_external_values


API_DIR = Path(__file__).resolve().parent


class BuildMockSpecTest(unittest.TestCase):
    def test_materializes_every_local_external_example(self) -> None:
        canonical = yaml.safe_load((API_DIR / "openapi.yaml").read_text())
        external_references = count_external_values(canonical)
        document, materialized = build_mock_document(API_DIR / "openapi.yaml")

        self.assertEqual(external_references, 68)
        self.assertEqual(materialized, external_references)
        self.assertEqual(count_external_values(document), 0)

        privacy = document["paths"]["/me/privacy"]["put"]
        withdrawal = privacy["responses"]["200"]["content"]["application/json"][
            "examples"
        ]["withdrawn"]["value"]
        expected = json.loads(
            (API_DIR / "examples/privacy-settings-response.json").read_text()
        )
        self.assertEqual(withdrawal, expected)

        stale = privacy["responses"]["412"]["content"][
            "application/problem+json"
        ]["examples"]["staleDelayedOptIn"]["value"]
        expected_stale = json.loads(
            (API_DIR / "examples/privacy-settings-stale-conflict.json").read_text()
        )
        self.assertEqual(stale, expected_stale)

        cancelled = document["components"]["responses"]["QuestCancelled"][
            "content"
        ]["application/json"]["examples"]["cancelled"]["value"]
        expected_cancelled = json.loads(
            (API_DIR / "examples/quest-cancelled-response.json").read_text()
        )
        self.assertEqual(cancelled, expected_cancelled)

        privacy_etag = document["components"]["headers"]["PrivacyETag"]
        self.assertEqual(privacy_etag["example"], '"privacy-v7"')
        validate(document)


if __name__ == "__main__":
    unittest.main()
