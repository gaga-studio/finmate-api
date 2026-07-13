#!/usr/bin/env python3
"""Tests for the deterministic Prism mock-spec builder and key vNext invariants."""

from __future__ import annotations

import json
import sys
import unittest
import warnings
from pathlib import Path

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

    def example(self, name: str) -> dict[str, object]:
        return json.loads((API_DIR / "examples" / name).read_text(encoding="utf-8"))

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
            "title": "검증용 루틴",
            "targetKind": target_kind,
            "steps": ["확인하기"],
            **target,
        }

    def test_builds_valid_contract_and_materializes_all_examples(self) -> None:
        canonical = yaml.safe_load((API_DIR / "openapi.yaml").read_text(encoding="utf-8"))
        self.assertEqual(self.materialized, count_external_values(canonical))
        self.assertEqual(count_external_values(self.document), 0)
        validate(self.document)

        operation_ids = {
            operation["operationId"]
            for path_item in self.document["paths"].values()
            for method, operation in path_item.items()
            if method in {"get", "post", "put", "patch", "delete"}
        }
        for operation_id in (
            "confirmUserGoal",
            "getCharacterReport",
            "getMateFriendOverview",
            "getMateGroupReport",
            "getAdventurerReport",
            "createRoutineRecommendation",
            "getRelatedHanaProductInfo",
            "acceptQuest",
            "getDailyJourneyMonth",
            "advanceDemoTimeline",
        ):
            self.assertIn(operation_id, operation_ids)
        self.assertNotIn("chooseRoutineAdaptationDomain", operation_ids)

    def test_onboarding_and_home_support_explore_before_goal(self) -> None:
        onboarding_schema = self.document["components"]["schemas"]["CompleteOnboardingRequest"]
        self.assertNotIn("mainGoal", onboarding_schema["properties"])
        self.assertNotIn("goal", onboarding_schema["properties"])
        self.assertEqual(
            set(self.document["components"]["schemas"]["OnboardingState"]["enum"]),
            {"EXPLORE_ONLY", "GOAL_ACTIVE"},
        )

        home_validator = self.validator("HomeView")
        explore = self.example("home-explore-response.json")
        active = self.example("home-response.json")
        home_validator.validate(explore)
        home_validator.validate(active)
        self.assertNotIn("mainGoal", explore)
        self.assertNotIn("raid", explore)
        self.assertEqual(
            set(explore["lockedActions"]),
            {"RAID", "QUEST_ACCEPT", "ROUTINE_IMPORT", "PERSONALIZED_PRODUCT_INFO"},
        )

    def test_candidate_domain_and_target_combinations_are_structural(self) -> None:
        validator = self.validator("RoutineAdaptationCandidate")
        validator.validate(
            self.candidate(
                domain="INVESTMENT_JUDGMENT",
                behaviorTarget="위험성향 점검표 완료",
            )
        )
        validator.validate(
            self.candidate(
                target_kind="AMOUNT_KRW",
                targetAmountKrw=500000,
            )
        )

        invalid_candidates = [
            self.candidate(
                domain="INVESTMENT_JUDGMENT",
                target_kind="AMOUNT_KRW",
                targetAmountKrw=500000,
            ),
            self.candidate(domain="INVESTMENT_JUDGMENT"),
            self.candidate(
                target_kind="AMOUNT_KRW",
                targetAmountKrw=500000,
                behaviorTarget="중복 목표",
            ),
        ]
        for candidate in invalid_candidates:
            with self.subTest(candidate=candidate):
                with self.assertRaises(ValidationError):
                    validator.validate(candidate)

    def test_routine_recommendation_is_recommendation_first_with_three_unique_options(self) -> None:
        recommendation = self.example("routine-recommendation-response.json")
        self.validator("RoutineRecommendation").validate(recommendation)
        options = recommendation["intensityOptions"]
        self.assertEqual(len(options), 3)
        self.assertEqual(
            {option["difficulty"] for option in options},
            {"LIGHT", "STANDARD", "CHALLENGE"},
        )
        self.assertEqual(len({option["candidateId"] for option in options}), 3)
        self.assertIn(
            recommendation["recommendedCandidate"]["candidateId"],
            {option["candidateId"] for option in options},
        )
        self.assertTrue(
            all(option["domain"] == recommendation["selectedDomain"] for option in options)
        )

    def test_mate_group_variants_reject_small_operational_groups(self) -> None:
        validator = self.validator("MateGroup")
        operational = {
            "groupId": "group-prod",
            "name": "운영 그룹",
            "memberCount": 30,
            "syntheticDemo": False,
            "eligibleForProductionAggregation": True,
        }
        demo = {
            "groupId": "group-demo",
            "name": "합성 그룹",
            "memberCount": 10,
            "syntheticDemo": True,
            "eligibleForProductionAggregation": False,
        }
        validator.validate(operational)
        validator.validate(demo)
        with self.assertRaises(ValidationError):
            validator.validate({**operational, "memberCount": 29})
        with self.assertRaises(ValidationError):
            validator.validate({**demo, "eligibleForProductionAggregation": True})

    def test_record_and_demo_examples_reconcile(self) -> None:
        journey = self.example("daily-journey-response.json")
        self.validator("DailyJourneyMonth").validate(journey)
        self.assertEqual(len(journey["nodes"]), journey["dayCount"])
        dates = [node["date"] for node in journey["nodes"]]
        self.assertEqual(dates, sorted(dates))
        day9 = next(node for node in journey["nodes"] if node["date"] == "2026-07-09")
        day11 = next(node for node in journey["nodes"] if node["date"] == "2026-07-11")
        self.assertNotEqual(day9["primaryActivity"]["title"], day11["primaryActivity"]["title"])
        self.assertEqual(day11["primaryActivity"]["activityType"], "INCOME")
        self.assertEqual(day11["primaryActivity"]["amountKrw"], 2800000)

        record = self.example("daily-record-response.json")
        budget = record["budget"]
        self.assertEqual(budget["budgetKrw"] - budget["spentKrw"], budget["remainingKrw"])
        monetary = [item for item in record["activities"] if "amountKrw" in item]
        primary = max(monetary, key=lambda item: abs(item["amountKrw"]))
        self.assertTrue(primary["primary"])
        self.assertEqual(primary["activityType"], "INCOME")

        demo = self.example("demo-timeline-response.json")
        self.validator("DemoTimelineView").validate(demo)
        self.assertEqual(len(demo["frames"]), 6)
        self.assertTrue(all(frame["savingEventKrw"] == 500000 for frame in demo["frames"]))
        self.assertEqual(demo["frames"][-1]["goalCurrentAmountKrw"], 5000000)
        self.assertEqual(demo["mainGoal"]["state"], "COMPLETED")

    def test_product_information_is_read_only_and_growth_neutral(self) -> None:
        product = self.example("hana-product-info-response.json")
        self.validator("RelatedHanaProductInfo").validate(product)
        self.assertFalse(product["inAppEnrollmentAvailable"])
        self.assertFalse(product["affectsProgress"])
        product_path = self.document["paths"]["/hana-products/{productId}"]
        self.assertEqual(set(product_path), {"get"})

    def test_auth_contract_uses_secure_refresh_cookie_boundary(self) -> None:
        schemas = self.document["components"]["schemas"]
        signup = schemas["SignUpRequest"]
        self.assertEqual(set(signup["required"]), {"email", "password", "displayName"})
        self.assertEqual(signup["properties"]["password"]["minLength"], 12)
        session = schemas["AuthSession"]
        self.assertEqual(set(session["required"]), {"accessToken", "tokenType", "expiresAt", "user"})
        self.assertNotIn("refreshToken", session["properties"])
        self.assertEqual(session["properties"]["tokenType"]["enum"], ["Bearer"])

        operations = {
            operation["operationId"]: operation
            for path_item in self.document["paths"].values()
            for method, operation in path_item.items()
            if method in {"get", "post", "put", "patch", "delete"}
        }
        for operation_id in ("refreshSession", "logOut"):
            operation = operations[operation_id]
            self.assertNotIn("requestBody", operation)
            parameter = operation["parameters"][0]
            resolved = self.document["components"]["parameters"][parameter["$ref"].rsplit("/", 1)[-1]]
            self.assertEqual((resolved["in"], resolved["name"]), ("cookie", "finmate_refresh"))


if __name__ == "__main__":
    unittest.main()
