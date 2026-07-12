#!/usr/bin/env python3
"""Verify the canonical FinMate vNext documentation and OpenAPI contract."""

from __future__ import annotations

import json
import re
import sys
import warnings
from pathlib import Path
from typing import Any

import yaml
warnings.filterwarnings("ignore", category=DeprecationWarning, message=r"jsonschema.RefResolver.*")
from jsonschema import FormatChecker, RefResolver
from openapi_schema_validator import OAS30Validator
from openapi_spec_validator import validate_spec


API_DIR = Path(__file__).resolve().parent
VNEXT_DIR = API_DIR.parent
LEGACY_DIR = VNEXT_DIR.parent / "legacy" / "finmate-vnext-pre-rpg"
OPENAPI_PATH = API_DIR / "openapi.yaml"
HTTP_METHODS = {"get", "post", "put", "patch", "delete"}

REQUIRED_SCHEMAS = {
    "UserGoal",
    "RecommendedAdventurerCard",
    "RoutineAdaptationCandidate",
    "ActiveRoutineBuild",
    "RaidView",
    "Quest",
    "DailyRecord",
}
REQUIRED_OPERATIONS = {
    "signUp",
    "logIn",
    "getOnboarding",
    "completeOnboarding",
    "getHome",
    "getCurrentRaid",
    "getMonthlyReport",
    "listMateGroups",
    "listRecommendedAdventurers",
    "getAdventurerRoutine",
    "createRoutineAdaptation",
    "chooseRoutineAdaptationDomain",
    "importRoutineAdaptationCandidate",
    "getActiveRoutineBuild",
    "replaceActiveRoutineBuild",
    "getActiveUserGoal",
    "listQuests",
    "completeQuest",
    "listDailyRecords",
    "getDailyRecord",
    "saveDailyReflection",
    "advanceDemoTimeline",
}
CALCULATED_SCHEMAS = {
    "HomeResponse",
    "RaidView",
    "MonthlyReport",
    "RecommendedAdventurerPage",
    "RoutineAdaptationSet",
    "ActiveRoutineBuild",
    "QuestPage",
    "DailyRecordPage",
    "DailyRecord",
}
FORBIDDEN_ACTIVE_PATTERNS = {
    r"Java 17": "Java 17",
    r"finmate-frontend": "old frontend repository",
    r"FinRoom": "FinRoom",
    r"\bRoadmap\b": "Roadmap",
    r"goal candidate": "goal-from-adventurer assumption",
    r"GoalCandidate": "goal-from-adventurer assumption",
    r"목표 후보": "goal-from-adventurer assumption",
    r"AI_GENERATED": "runtime-generated recommendation assumption",
    r"LLM runtime": "LLM runtime",
}
EXAMPLE_SCHEMAS = {
    "adventurers-response.json": "RecommendedAdventurerPage",
    "auth-login-request.json": "LoginRequest",
    "auth-session-response.json": "AuthSession",
    "auth-signup-request.json": "SignUpRequest",
    "daily-record-response.json": "DailyRecord",
    "data-insufficient-problem.json": "Problem",
    "data-stale-problem.json": "Problem",
    "demo-timeline-response.json": "DemoTimelineView",
    "home-response.json": "HomeResponse",
    "mate-groups-response.json": "MateGroupPage",
    "monthly-report-response.json": "MonthlyReport",
    "onboarding-request.json": "CompleteOnboardingRequest",
    "onboarding-response.json": "OnboardingView",
    "quest-list-response.json": "QuestPage",
    "raid-response.json": "RaidView",
    "routine-adaptation-response.json": "RoutineAdaptationSet",
    "routine-build-replacement-response.json": "RoutineBuildReplacement",
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def operations(spec: dict[str, Any]) -> dict[str, tuple[str, str, dict[str, Any]]]:
    found: dict[str, tuple[str, str, dict[str, Any]]] = {}
    for path, path_item in spec.get("paths", {}).items():
        for method, operation in path_item.items():
            if method in HTTP_METHODS and isinstance(operation, dict):
                operation_id = operation.get("operationId")
                if operation_id:
                    found[operation_id] = (method.upper(), path, operation)
    return found


def resolve_schema(spec: dict[str, Any], value: dict[str, Any]) -> dict[str, Any]:
    if "$ref" not in value:
        return value
    prefix = "#/components/schemas/"
    ref = value["$ref"]
    if not ref.startswith(prefix):
        raise ValueError(f"unsupported schema ref: {ref}")
    return spec["components"]["schemas"][ref[len(prefix) :]]


def check_schema_contract(spec: dict[str, Any], errors: list[str]) -> None:
    schemas = spec.get("components", {}).get("schemas", {})
    missing = REQUIRED_SCHEMAS - set(schemas)
    if missing:
        fail(errors, f"missing required schemas: {sorted(missing)}")

    for name in CALCULATED_SCHEMAS:
        schema = schemas.get(name)
        if not schema:
            continue
        props = resolve_schema(spec, schema).get("properties", {})
        required = set(resolve_schema(spec, schema).get("required", []))
        metadata = {"calculationVersion", "dataState", "lastSyncedAt"}
        absent = metadata - set(props)
        if absent or not metadata <= required:
            fail(errors, f"{name} must require calculationVersion, dataState and lastSyncedAt")

    krw = schemas.get("KrwAmount", {})
    if krw.get("type") != "integer" or krw.get("format") != "int64":
        fail(errors, "KrwAmount must be an int64 integer")
    bps = schemas.get("BasisPoints", {})
    if bps.get("type") != "integer" or bps.get("minimum") != 0 or bps.get("maximum") != 10000:
        fail(errors, "BasisPoints must be an integer in [0, 10000]")
    timestamp = schemas.get("Timestamp", {})
    if timestamp.get("type") != "string" or timestamp.get("format") != "date-time":
        fail(errors, "Timestamp must use ISO 8601 date-time")


def schema_validator(spec: dict[str, Any], schema_name: str) -> OAS30Validator:
    return OAS30Validator(
        spec["components"]["schemas"][schema_name],
        resolver=RefResolver.from_schema(spec),
        format_checker=FormatChecker(),
    )


def expect_valid(
    validator: OAS30Validator,
    payload: dict[str, Any],
    label: str,
    errors: list[str],
) -> None:
    validation_errors = list(validator.iter_errors(payload))
    if validation_errors:
        fail(errors, f"{label} must validate: {validation_errors[0].message}")


def expect_invalid(
    validator: OAS30Validator,
    payload: dict[str, Any],
    label: str,
    errors: list[str],
) -> None:
    if not list(validator.iter_errors(payload)):
        fail(errors, f"{label} must be rejected by schema validation")


def adaptation_candidate(
    *,
    candidate_id: str = "candidate-light",
    difficulty: str = "LIGHT",
    domain: str = "SAVING",
    target_kind: str = "BEHAVIOR",
    **target: Any,
) -> dict[str, Any]:
    return {
        "candidateId": candidate_id,
        "difficulty": difficulty,
        "domain": domain,
        "title": "Weekly behavior",
        "targetKind": target_kind,
        "steps": ["Check on Friday"],
        **target,
    }


def check_structural_variants(spec: dict[str, Any], errors: list[str]) -> int:
    checks = 0
    candidate_validator = schema_validator(spec, "RoutineAdaptationCandidate")
    valid_candidates = [
        adaptation_candidate(
            domain="INVESTMENT_JUDGMENT",
            behaviorTarget="Complete the risk-profile checklist",
        ),
        adaptation_candidate(
            domain="SAVING",
            target_kind="AMOUNT_KRW",
            targetAmountKrw=50000,
        ),
    ]
    invalid_candidates = [
        adaptation_candidate(
            domain="INVESTMENT_JUDGMENT",
            target_kind="AMOUNT_KRW",
            targetAmountKrw=50000,
        ),
        adaptation_candidate(domain="INVESTMENT_JUDGMENT"),
        adaptation_candidate(behaviorTarget="Weekly check", targetAmountKrw=50000),
        adaptation_candidate(domain="FINANCIAL_KNOWLEDGE", behaviorTarget="Learning log"),
    ]
    for index, payload in enumerate(valid_candidates, start=1):
        expect_valid(candidate_validator, payload, f"valid adaptation candidate {index}", errors)
        checks += 1
    for index, payload in enumerate(invalid_candidates, start=1):
        expect_invalid(candidate_validator, payload, f"invalid adaptation candidate {index}", errors)
        checks += 1

    light = adaptation_candidate(behaviorTarget="Light check")
    standard = adaptation_candidate(
        candidate_id="candidate-standard",
        difficulty="STANDARD",
        behaviorTarget="Standard check",
    )
    challenge = adaptation_candidate(
        candidate_id="candidate-challenge",
        difficulty="CHALLENGE",
        behaviorTarget="Challenge check",
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
    adaptation_validator = schema_validator(spec, "RoutineAdaptationSet")
    expect_valid(adaptation_validator, adaptation, "exact difficulty adaptation set", errors)
    expect_invalid(
        adaptation_validator,
        {key: value for key, value in adaptation.items() if key != "challenge"},
        "adaptation set missing CHALLENGE",
        errors,
    )
    expect_invalid(
        adaptation_validator,
        {**adaptation, "standard": {**standard, "difficulty": "LIGHT"}},
        "adaptation set with duplicate LIGHT",
        errors,
    )
    checks += 3

    group_validator = schema_validator(spec, "MateGroup")
    valid_groups = [
        {
            "groupId": "group-prod",
            "name": "Operational",
            "memberCount": 30,
            "syntheticDemo": False,
            "eligibleForProductionAggregation": True,
        },
        {
            "groupId": "group-demo",
            "name": "Synthetic demo",
            "memberCount": 10,
            "syntheticDemo": True,
            "eligibleForProductionAggregation": False,
        },
    ]
    invalid_groups = [
        {**valid_groups[0], "memberCount": 10},
        {**valid_groups[1], "eligibleForProductionAggregation": True},
        {**valid_groups[1], "syntheticDemo": False},
    ]
    for index, payload in enumerate(valid_groups, start=1):
        expect_valid(group_validator, payload, f"valid mate group {index}", errors)
        checks += 1
    for index, payload in enumerate(invalid_groups, start=1):
        expect_invalid(group_validator, payload, f"invalid mate group {index}", errors)
        checks += 1
    return checks


def resolve_parameter(spec: dict[str, Any], parameter: dict[str, Any]) -> dict[str, Any]:
    if "$ref" not in parameter:
        return parameter
    return spec["components"]["parameters"][parameter["$ref"].rsplit("/", 1)[-1]]


def check_auth_contract(spec: dict[str, Any], errors: list[str]) -> int:
    schemas = spec["components"]["schemas"]
    signup = schemas["SignUpRequest"]
    if set(signup.get("required", [])) != {"email", "password", "displayName"}:
        fail(errors, "SignUpRequest must require email, password and displayName")
    password = signup.get("properties", {}).get("password", {})
    if (password.get("minLength"), password.get("maxLength")) != (12, 72):
        fail(errors, "signup password must be constrained to 12..72 characters")

    session = schemas["AuthSession"]
    if set(session.get("required", [])) != {"accessToken", "tokenType", "expiresAt", "user"}:
        fail(errors, "AuthSession must require accessToken, tokenType, expiresAt and nested user")
    if "refreshToken" in session.get("properties", {}):
        fail(errors, "AuthSession must never expose a refresh token")

    found = operations(spec)
    for operation_id in ("refreshSession", "logOut"):
        operation = found.get(operation_id, (None, None, {}))[2]
        if "requestBody" in operation:
            fail(errors, f"{operation_id} must not accept a JSON refresh token body")
        parameters = [resolve_parameter(spec, value) for value in operation.get("parameters", [])]
        cookies = [
            value
            for value in parameters
            if value.get("in") == "cookie" and value.get("name") == "finmate_refresh"
        ]
        if len(cookies) != 1:
            fail(errors, f"{operation_id} must consume the finmate_refresh cookie")

    required_problem = {"type", "title", "status", "detail", "instance", "code", "traceId"}
    problem = schemas["Problem"]
    if not required_problem <= set(problem.get("required", [])):
        fail(errors, "Problem must require complete RFC 7807 metadata and traceId")
    codes = set(problem.get("properties", {}).get("code", {}).get("enum", []))
    if not {"INVALID_CREDENTIALS", "DUPLICATE_EMAIL"} <= codes:
        fail(errors, "Problem codes must include INVALID_CREDENTIALS and DUPLICATE_EMAIL")
    return 7


def check_operations(spec: dict[str, Any], errors: list[str]) -> None:
    found = operations(spec)
    missing = REQUIRED_OPERATIONS - set(found)
    if missing:
        fail(errors, f"missing required operationIds: {sorted(missing)}")
    demo = found.get("advanceDemoTimeline")
    if demo and demo[:2] != ("POST", "/demo/timeline/advance"):
        fail(errors, "demo advancement must be POST /demo/timeline/advance")
    for operation_id, (_, _, operation) in found.items():
        responses = operation.get("responses", {})
        has_problem_default = responses.get("default", {}).get("$ref") == "#/components/responses/ProblemResponse"
        if not has_problem_default and not any(str(code).startswith(("4", "5")) for code in responses):
            fail(errors, f"{operation_id} must declare an RFC 7807 error response")


def check_examples(spec: dict[str, Any], errors: list[str]) -> int:
    example_count = 0
    for path in sorted(API_DIR.joinpath("examples").glob("*.json")):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            example_count += 1
        except json.JSONDecodeError as exc:
            fail(errors, f"invalid JSON example {path.name}: {exc}")
            continue
        schema_name = EXAMPLE_SCHEMAS.get(path.name)
        if schema_name is None:
            fail(errors, f"example has no schema-validation mapping: {path.name}")
            continue
        expect_valid(
            schema_validator(spec, schema_name),
            payload,
            f"example {path.name} against {schema_name}",
            errors,
        )
    missing_examples = set(EXAMPLE_SCHEMAS) - {
        path.name for path in API_DIR.joinpath("examples").glob("*.json")
    }
    if missing_examples:
        fail(errors, f"missing mapped examples: {sorted(missing_examples)}")
    if example_count < 9:
        fail(errors, "at least nine endpoint examples are required")
    return example_count


def check_docs(errors: list[str]) -> None:
    current_docs = sorted(
        path
        for path in VNEXT_DIR.rglob("*")
        if path.is_file() and path.suffix in {".md", ".yaml", ".yml"}
    )
    combined = "\n".join(path.read_text(encoding="utf-8") for path in current_docs)
    required_phrases = {
        "gaga-studio/finmate-api",
        "gaga-studio/finmate-web",
        "Java 21",
        "PostgreSQL",
        "React",
        "TypeScript",
        "Vite PWA",
        "홈",
        "메이트",
        "퀘스트",
        "기록",
        "30",
        "10",
        "SYNTHETIC",
        "DETERMINISTIC_APPROVED_COPY",
    }
    for phrase in sorted(required_phrases):
        if phrase not in combined:
            fail(errors, f"canonical docs missing binding phrase: {phrase}")
    for pattern, label in FORBIDDEN_ACTIVE_PATTERNS.items():
        if re.search(pattern, combined, re.IGNORECASE):
            fail(errors, f"docs/vnext retains forbidden active decision: {label}")
    archive_readme = LEGACY_DIR / "README.md"
    if not archive_readme.exists() or "ARCHIVED" not in archive_readme.read_text(encoding="utf-8"):
        fail(errors, "pre-RPG artifacts must live under a legacy archive with an ARCHIVED marker")


def main() -> int:
    errors: list[str] = []
    spec = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    try:
        validate_spec(spec)
    except Exception as exc:  # validator provides the actionable path
        fail(errors, f"OpenAPI validation failed: {exc}")
    check_schema_contract(spec, errors)
    structural_checks = check_structural_variants(spec, errors)
    check_operations(spec, errors)
    auth_checks = check_auth_contract(spec, errors)
    example_count = check_examples(spec, errors)
    check_docs(errors)
    if errors:
        print("CONTRACT_VERIFICATION_FAILED")
        for error in errors:
            print(f"- {error}")
        return 1
    print(
        "CONTRACT_VERIFICATION_OK "
        f"operations={len(operations(spec))} schemas={len(spec['components']['schemas'])} "
        f"examples={example_count} structuralChecks={structural_checks} authChecks={auth_checks}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
