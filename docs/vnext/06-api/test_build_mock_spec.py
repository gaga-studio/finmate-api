#!/usr/bin/env python3
"""Tests for the deterministic Prism mock-spec builder."""

from __future__ import annotations

import unittest
from pathlib import Path
import sys
import warnings

import yaml
warnings.filterwarnings("ignore", category=DeprecationWarning, message=r"jsonschema.RefResolver.*")
from jsonschema import FormatChecker, RefResolver, ValidationError
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import validate

API_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(API_DIR))

from build_mock_spec import build_mock_document, count_external_values


class BuildMockSpecTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document, cls.materialized = build_mock_document(API_DIR / "openapi.yaml")
        cls.resolver = RefResolver.from_schema(cls.document)

    def validator(self, schema_name: str) -> OAS30Validator:
        return OAS30Validator(
            self.document["components"]["schemas"][schema_name],
            resolver=self.resolver,
            format_checker=FormatChecker(),
        )

    @staticmethod
    def candidate(
        *,
        candidate_id: str = "candidate-light",
        difficulty: str = "LIGHT",
        domain: str = "SAVING",
        target_kind: str = "BEHAVIOR",
        **target: object,
    ) -> dict[str, object]:
        return {
            "candidateId": candidate_id,
            "difficulty": difficulty,
            "domain": domain,
            "title": "주간 행동",
            "targetKind": target_kind,
            "steps": ["금요일에 확인하기"],
            **target,
        }

    def test_builds_valid_contract_without_unresolved_external_examples(self) -> None:
        canonical = yaml.safe_load((API_DIR / "openapi.yaml").read_text())

        self.assertEqual(self.materialized, count_external_values(canonical))
        self.assertEqual(count_external_values(self.document), 0)
        self.assertEqual(
            self.document["paths"]["/demo/timeline/advance"]["post"]["operationId"],
            "advanceDemoTimeline",
        )
        self.assertIn("ActiveRoutineBuild", self.document["components"]["schemas"])
        validate(self.document)

    def test_candidate_domain_and_target_combinations_are_structural(self) -> None:
        validator = self.validator("RoutineAdaptationCandidate")
        validator.validate(
            self.candidate(
                domain="INVESTMENT_JUDGMENT",
                behaviorTarget="위험 성향 점검표를 완료한다",
            )
        )
        validator.validate(
            self.candidate(
                domain="SAVING",
                target_kind="AMOUNT_KRW",
                targetAmountKrw=50000,
            )
        )

        invalid_candidates = [
            self.candidate(
                domain="INVESTMENT_JUDGMENT",
                target_kind="AMOUNT_KRW",
                targetAmountKrw=50000,
            ),
            self.candidate(domain="INVESTMENT_JUDGMENT"),
            self.candidate(behaviorTarget="주간 확인", targetAmountKrw=50000),
            self.candidate(domain="FINANCIAL_KNOWLEDGE", behaviorTarget="학습 기록"),
        ]
        for candidate in invalid_candidates:
            with self.subTest(candidate=candidate):
                with self.assertRaises(ValidationError):
                    validator.validate(candidate)

    def test_adaptation_set_requires_exact_difficulty_slots(self) -> None:
        validator = self.validator("RoutineAdaptationSet")
        light = self.candidate(behaviorTarget="가볍게 확인")
        standard = self.candidate(
            candidate_id="candidate-standard",
            difficulty="STANDARD",
            behaviorTarget="표준 확인",
        )
        challenge = self.candidate(
            candidate_id="candidate-challenge",
            difficulty="CHALLENGE",
            behaviorTarget="도전 확인",
        )
        adaptation = {
            "adaptationId": "adapt-1",
            "sourceRoutineId": "routine-1",
            "state": "CANDIDATES_READY",
            "selectedDomain": "SAVING",
            "light": light,
            "standard": standard,
            "challenge": challenge,
            "calculationVersion": "adapt-calc-v1",
            "dataState": "FRESH",
            "lastSyncedAt": "2026-07-13T09:00:00+09:00",
        }
        validator.validate(adaptation)

        for mutation in (
            {key: value for key, value in adaptation.items() if key != "challenge"},
            {**adaptation, "standard": {**standard, "difficulty": "LIGHT"}},
            {**adaptation, "standard": {**standard, "domain": "SPENDING"}},
        ):
            with self.assertRaises(ValidationError):
                validator.validate(mutation)

        investment_light = self.candidate(
            domain="INVESTMENT_JUDGMENT",
            behaviorTarget="가볍게 위험 성향 확인",
        )
        investment_standard = self.candidate(
            candidate_id="candidate-investment-standard",
            difficulty="STANDARD",
            domain="INVESTMENT_JUDGMENT",
            behaviorTarget="표준 위험 성향 확인",
        )
        investment_challenge = self.candidate(
            candidate_id="candidate-investment-challenge",
            difficulty="CHALLENGE",
            domain="INVESTMENT_JUDGMENT",
            behaviorTarget="도전 위험 성향 확인",
        )
        investment_adaptation = {
            **adaptation,
            "selectedDomain": "INVESTMENT_JUDGMENT",
            "light": investment_light,
            "standard": investment_standard,
            "challenge": investment_challenge,
        }
        validator.validate(investment_adaptation)
        with self.assertRaises(ValidationError):
            validator.validate(
                {
                    **investment_adaptation,
                    "challenge": {**investment_challenge, "domain": "SAVING"},
                }
            )

        spending_adaptation = {
            **adaptation,
            "selectedDomain": "SPENDING",
            "light": {**light, "domain": "SPENDING"},
            "standard": {**standard, "domain": "SPENDING"},
            "challenge": {**challenge, "domain": "SPENDING"},
        }
        validator.validate(spending_adaptation)
        with self.assertRaises(ValidationError):
            validator.validate(
                {
                    **spending_adaptation,
                    "light": {**light, "domain": "SAVING"},
                }
            )

    def test_mate_group_variants_reject_small_production_groups(self) -> None:
        validator = self.validator("MateGroup")
        validator.validate(
            {
                "groupId": "group-prod",
                "name": "운영 그룹",
                "memberCount": 30,
                "syntheticDemo": False,
                "eligibleForProductionAggregation": True,
            }
        )
        validator.validate(
            {
                "groupId": "group-demo",
                "name": "데모 그룹",
                "memberCount": 10,
                "syntheticDemo": True,
                "eligibleForProductionAggregation": False,
            }
        )

        invalid_groups = [
            {
                "groupId": "group-small-prod",
                "name": "잘못된 운영 그룹",
                "memberCount": 10,
                "syntheticDemo": False,
                "eligibleForProductionAggregation": True,
            },
            {
                "groupId": "group-demo-prod",
                "name": "잘못된 데모 그룹",
                "memberCount": 10,
                "syntheticDemo": True,
                "eligibleForProductionAggregation": True,
            },
        ]
        for group in invalid_groups:
            with self.subTest(group=group):
                with self.assertRaises(ValidationError):
                    validator.validate(group)

    def test_auth_contract_uses_secure_refresh_cookie_boundary(self) -> None:
        schemas = self.document["components"]["schemas"]
        signup = schemas["SignUpRequest"]
        self.assertEqual(set(signup["required"]), {"email", "password", "displayName"})
        self.assertEqual(signup["properties"]["password"]["minLength"], 12)

        session = schemas["AuthSession"]
        self.assertEqual(
            set(session["required"]),
            {"accessToken", "tokenType", "expiresAt", "user"},
        )
        self.assertNotIn("refreshToken", session["properties"])
        self.assertEqual(session["properties"]["tokenType"]["enum"], ["Bearer"])

        operations = {
            operation["operationId"]: operation
            for path_item in self.document["paths"].values()
            for method, operation in path_item.items()
            if method in {"get", "post", "put", "patch", "delete"}
            and operation.get("operationId")
        }

        for operation_id in ("refreshSession", "logOut"):
            operation = operations[operation_id]
            self.assertNotIn("requestBody", operation)
            parameters = []
            for parameter in operation.get("parameters", []):
                if "$ref" in parameter:
                    parameters.append(
                        self.document["components"]["parameters"][
                            parameter["$ref"].rsplit("/", 1)[-1]
                        ]
                    )
                else:
                    parameters.append(parameter)
            cookie_parameters = [
                parameter
                for parameter in parameters
                if parameter.get("in") == "cookie"
                and parameter.get("name") == "finmate_refresh"
            ]
            self.assertEqual(len(cookie_parameters), 1)

        expected_rotating_cookie = {
            "x-cookie-name": "finmate_refresh",
            "x-http-only": True,
            "x-same-site": "Lax",
            "x-path": "/api/v1/auth",
            "x-max-age-seconds": 2592000,
        }
        for operation_id, status in (
            ("signUp", "201"),
            ("logIn", "200"),
            ("refreshSession", "200"),
        ):
            response = operations[operation_id]["responses"][status]
            if "$ref" in response:
                response = self.document["components"]["responses"][
                    response["$ref"].rsplit("/", 1)[-1]
                ]
            header = response.get("headers", {}).get("Set-Cookie")
            self.assertIsNotNone(header, f"{operation_id} must rotate Set-Cookie")
            if "$ref" in header:
                header = self.document["components"]["headers"][
                    header["$ref"].rsplit("/", 1)[-1]
                ]
            for key, value in expected_rotating_cookie.items():
                self.assertEqual(header.get(key), value, f"{operation_id} {key}")

        logout = operations["logOut"]["responses"]["204"]
        cleared = logout.get("headers", {}).get("Set-Cookie")
        self.assertIsNotNone(cleared, "logout must clear Set-Cookie")
        if "$ref" in cleared:
            cleared = self.document["components"]["headers"][
                cleared["$ref"].rsplit("/", 1)[-1]
            ]
        for key, value in {
            **expected_rotating_cookie,
            "x-max-age-seconds": 0,
            "x-expires-immediately": True,
        }.items():
            self.assertEqual(cleared.get(key), value, f"logout {key}")

        problem = schemas["Problem"]
        self.assertTrue(
            {"type", "title", "status", "detail", "instance", "code", "traceId"}
            <= set(problem["required"])
        )
        self.assertTrue(
            {"INVALID_CREDENTIALS", "DUPLICATE_EMAIL"}
            <= set(problem["properties"]["code"]["enum"])
        )

    def test_goal_contract_matches_implementation_error_surface(self) -> None:
        schemas = self.document["components"]["schemas"]
        draft = schemas["UserGoalDraft"]
        self.assertIn("currentAmountKrw", draft["required"])
        self.assertEqual(draft["properties"]["title"]["maxLength"], 255)
        self.assertEqual(schemas["UserGoal"]["properties"]["title"]["maxLength"], 255)

        problem_codes = set(schemas["Problem"]["properties"]["code"]["enum"])
        self.assertIn("ACTIVE_MAIN_GOAL_EXISTS", problem_codes)
        self.assertIn("VALIDATION_FAILED", problem_codes)
        self.assertIn("NOT_FOUND", problem_codes)
        self.assertNotIn("MAIN_GOAL_NOT_FOUND", problem_codes)


if __name__ == "__main__":
    unittest.main()
