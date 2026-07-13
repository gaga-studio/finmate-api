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
    "OnboardingState",
    "UserGoal",
    "HomeView",
    "RaidView",
    "CharacterReport",
    "MateFriendOverview",
    "MateGroupReport",
    "RecommendedAdventurerCard",
    "AdventurerReport",
    "RoutineRecommendation",
    "RoutineAdaptationCandidate",
    "ActiveRoutineBuild",
    "RelatedHanaProductInfo",
    "Quest",
    "QuestAcceptance",
    "DailyJourneyMonth",
    "DailyRecord",
    "DemoTimelineView",
}
REQUIRED_OPERATIONS = {
    "signUp",
    "logIn",
    "refreshSession",
    "logOut",
    "getOnboarding",
    "completeOnboarding",
    "confirmUserGoal",
    "getActiveUserGoal",
    "getHome",
    "getCurrentRaid",
    "getCharacterReport",
    "getMonthlyReport",
    "getMateFriendOverview",
    "getMateFriendFeed",
    "listMateGroups",
    "getMateGroupReport",
    "listRecommendedAdventurers",
    "getRecommendedAdventurer",
    "getAdventurerReport",
    "getAdventurerRoutine",
    "searchMateAdventurers",
    "createRoutineRecommendation",
    "importRoutineAdaptationCandidate",
    "getActiveRoutineBuild",
    "replaceActiveRoutineBuild",
    "getRelatedHanaProductInfo",
    "listQuests",
    "getQuest",
    "acceptQuest",
    "completeQuest",
    "listDailyRecords",
    "getDailyJourneyMonth",
    "getDailyRecord",
    "saveDailyReflection",
    "advanceDemoTimeline",
}
CALCULATED_SCHEMAS = {
    "OnboardingView",
    "UserGoal",
    "HomeView",
    "RaidView",
    "CharacterReport",
    "MonthlyReport",
    "MateFriendOverview",
    "MateGroupReport",
    "RecommendedAdventurerPage",
    "AdventurerReport",
    "RoutineRecommendation",
    "ActiveRoutineBuild",
    "Quest",
    "QuestPage",
    "DailyRecord",
    "DailyRecordPage",
    "DailyJourneyMonth",
    "DemoTimelineView",
}
EXAMPLE_SCHEMAS = {
    "adventurer-report-response.json": "AdventurerReport",
    "adventurers-response.json": "RecommendedAdventurerPage",
    "auth-login-request.json": "LoginRequest",
    "auth-session-response.json": "AuthSession",
    "auth-signup-request.json": "SignUpRequest",
    "character-report-response.json": "CharacterReport",
    "daily-journey-response.json": "DailyJourneyMonth",
    "daily-record-response.json": "DailyRecord",
    "data-insufficient-problem.json": "Problem",
    "data-stale-problem.json": "Problem",
    "demo-timeline-response.json": "DemoTimelineView",
    "goal-confirm-request.json": "ConfirmUserGoalRequest",
    "goal-confirm-response.json": "UserGoal",
    "goal-required-problem.json": "Problem",
    "hana-product-info-response.json": "RelatedHanaProductInfo",
    "home-explore-response.json": "HomeView",
    "home-response.json": "HomeView",
    "mate-friend-overview-response.json": "MateFriendOverview",
    "mate-group-report-response.json": "MateGroupReport",
    "mate-groups-response.json": "MateGroupPage",
    "monthly-report-response.json": "MonthlyReport",
    "onboarding-request.json": "CompleteOnboardingRequest",
    "onboarding-response.json": "OnboardingView",
    "quest-accept-response.json": "QuestAcceptance",
    "quest-list-response.json": "QuestPage",
    "raid-response.json": "RaidView",
    "routine-build-replacement-response.json": "RoutineBuildReplacement",
    "routine-recommendation-response.json": "RoutineRecommendation",
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def load_example(name: str) -> dict[str, Any]:
    return json.loads((API_DIR / "examples" / name).read_text(encoding="utf-8"))


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
    return spec["components"]["schemas"][value["$ref"].rsplit("/", 1)[-1]]


def resolve_component(spec: dict[str, Any], component: str, value: dict[str, Any]) -> dict[str, Any]:
    if "$ref" not in value:
        return value
    return spec["components"][component][value["$ref"].rsplit("/", 1)[-1]]


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
    found = list(validator.iter_errors(payload))
    if found:
        fail(errors, f"{label} must validate: {found[0].message}")


def expect_invalid(
    validator: OAS30Validator,
    payload: dict[str, Any],
    label: str,
    errors: list[str],
) -> None:
    if not list(validator.iter_errors(payload)):
        fail(errors, f"{label} must be rejected by schema validation")


def check_schema_contract(spec: dict[str, Any], errors: list[str]) -> None:
    schemas = spec.get("components", {}).get("schemas", {})
    missing = REQUIRED_SCHEMAS - set(schemas)
    if missing:
        fail(errors, f"missing required schemas: {sorted(missing)}")

    metadata = {"calculationVersion", "dataState", "lastSyncedAt"}
    for name in CALCULATED_SCHEMAS:
        schema = resolve_schema(spec, schemas.get(name, {}))
        properties = schema.get("properties", {})
        required = set(schema.get("required", []))
        if not metadata <= set(properties) or not metadata <= required:
            fail(errors, f"{name} must require calculationVersion, dataState and lastSyncedAt")

    krw = schemas.get("KrwAmount", {})
    if (krw.get("type"), krw.get("format")) != ("integer", "int64"):
        fail(errors, "KrwAmount must be an int64 integer")
    bps = schemas.get("BasisPoints", {})
    if (bps.get("type"), bps.get("minimum"), bps.get("maximum")) != ("integer", 0, 10000):
        fail(errors, "BasisPoints must be an integer in [0, 10000]")


def candidate(
    candidate_id: str,
    difficulty: str,
    domain: str = "SAVING",
    target_kind: str = "BEHAVIOR",
    **target: Any,
) -> dict[str, Any]:
    return {
        "candidateId": candidate_id,
        "difficulty": difficulty,
        "domain": domain,
        "title": "검증용 루틴",
        "targetKind": target_kind,
        "steps": ["확인하기"],
        **target,
    }


def check_routine_contract(spec: dict[str, Any], errors: list[str]) -> int:
    checks = 0
    validator = schema_validator(spec, "RoutineAdaptationCandidate")
    valid = [
        candidate("saving-amount", "STANDARD", target_kind="AMOUNT_KRW", targetAmountKrw=500000),
        candidate(
            "investment-behavior",
            "LIGHT",
            domain="INVESTMENT_JUDGMENT",
            behaviorTarget="위험성향 확인",
        ),
    ]
    invalid = [
        candidate(
            "investment-amount",
            "STANDARD",
            domain="INVESTMENT_JUDGMENT",
            target_kind="AMOUNT_KRW",
            targetAmountKrw=500000,
        ),
        candidate("missing-target", "LIGHT"),
        candidate(
            "double-target",
            "LIGHT",
            target_kind="AMOUNT_KRW",
            targetAmountKrw=100000,
            behaviorTarget="중복",
        ),
    ]
    for index, payload in enumerate(valid, start=1):
        expect_valid(validator, payload, f"valid routine candidate {index}", errors)
        checks += 1
    for index, payload in enumerate(invalid, start=1):
        expect_invalid(validator, payload, f"invalid routine candidate {index}", errors)
        checks += 1

    recommendation = load_example("routine-recommendation-response.json")
    options = recommendation["intensityOptions"]
    difficulties = [item["difficulty"] for item in options]
    candidate_ids = [item["candidateId"] for item in options]
    if len(options) != 3 or set(difficulties) != {"LIGHT", "STANDARD", "CHALLENGE"}:
        fail(errors, "RoutineRecommendation must expose exactly one LIGHT, STANDARD and CHALLENGE option")
    if len(set(candidate_ids)) != 3:
        fail(errors, "RoutineRecommendation candidate IDs must be unique")
    if any(item["domain"] != recommendation["selectedDomain"] for item in options):
        fail(errors, "all intensity options must match selectedDomain")
    if recommendation["recommendedCandidate"]["candidateId"] not in candidate_ids:
        fail(errors, "recommendedCandidate must be one of intensityOptions")
    checks += 4
    return checks


def check_group_contract(spec: dict[str, Any], errors: list[str]) -> int:
    validator = schema_validator(spec, "MateGroup")
    valid = [
        {
            "groupId": "prod",
            "name": "운영 그룹",
            "memberCount": 30,
            "syntheticDemo": False,
            "eligibleForProductionAggregation": True,
        },
        {
            "groupId": "demo",
            "name": "합성 그룹",
            "memberCount": 10,
            "syntheticDemo": True,
            "eligibleForProductionAggregation": False,
        },
    ]
    invalid = [
        {**valid[0], "memberCount": 29},
        {**valid[1], "eligibleForProductionAggregation": True},
    ]
    for index, payload in enumerate(valid, start=1):
        expect_valid(validator, payload, f"valid group {index}", errors)
    for index, payload in enumerate(invalid, start=1):
        expect_invalid(validator, payload, f"invalid group {index}", errors)
    return 4


def check_product_flows(errors: list[str]) -> int:
    checks = 0
    onboarding_request = load_example("onboarding-request.json")
    onboarding_response = load_example("onboarding-response.json")
    if "mainGoal" in onboarding_request or "goal" in onboarding_request:
        fail(errors, "onboarding completion must not require or embed a goal")
    if onboarding_response["onboardingState"] != "EXPLORE_ONLY" or "mainGoal" in onboarding_response:
        fail(errors, "onboarding example must finish in goal-free EXPLORE_ONLY mode")
    checks += 2

    explore = load_example("home-explore-response.json")
    active = load_example("home-response.json")
    if explore["mode"] != "EXPLORE_ONLY" or "mainGoal" in explore or "raid" in explore:
        fail(errors, "explore home must omit mainGoal and raid")
    required_locks = {"RAID", "QUEST_ACCEPT", "ROUTINE_IMPORT", "PERSONALIZED_PRODUCT_INFO"}
    if set(explore["lockedActions"]) != required_locks:
        fail(errors, "explore home must lock all goal-dependent actions")
    if active["mode"] != "GOAL_ACTIVE" or not {"mainGoal", "raid"} <= set(active):
        fail(errors, "goal-active home must include mainGoal and raid")
    checks += 3

    product = load_example("hana-product-info-response.json")
    if product["affectsProgress"] is not False or product["inAppEnrollmentAvailable"] is not False:
        fail(errors, "Hana product information must not affect progress or offer in-app enrollment")
    checks += 1

    goal_required = load_example("goal-required-problem.json")
    if (goal_required["status"], goal_required["code"]) != (409, "GOAL_REQUIRED"):
        fail(errors, "goal-required problem must be RFC 7807 status 409 with GOAL_REQUIRED")
    checks += 1
    return checks


def check_record_and_demo(errors: list[str]) -> int:
    checks = 0
    journey = load_example("daily-journey-response.json")
    nodes = journey["nodes"]
    dates = [node["date"] for node in nodes]
    if len(nodes) != journey["dayCount"] or dates != sorted(dates) or len(set(dates)) != len(dates):
        fail(errors, "DailyJourneyMonth must contain each day exactly once in ascending order")
    day9 = next(node for node in nodes if node["date"] == "2026-07-09")
    day11 = next(node for node in nodes if node["date"] == "2026-07-11")
    if day9["primaryActivity"]["title"] == day11["primaryActivity"]["title"]:
        fail(errors, "July 9 and July 11 must not duplicate the same primary activity")
    primary11 = day11["primaryActivity"]
    if (primary11["activityType"], primary11.get("amountKrw")) != ("INCOME", 2800000):
        fail(errors, "July 11 journey primary activity must be income +2,800,000 KRW")
    if len(day11["secondaryActivityTypes"]) > 2:
        fail(errors, "journey nodes may expose at most two secondary activities")
    checks += 4

    record = load_example("daily-record-response.json")
    monetary = [activity for activity in record["activities"] if "amountKrw" in activity]
    largest = max(monetary, key=lambda item: abs(item["amountKrw"]))
    if not largest["primary"] or largest["activityType"] != "INCOME":
        fail(errors, "daily record primary activity must be the largest absolute monetary event")
    budget = record["budget"]
    if budget["budgetKrw"] - budget["spentKrw"] != budget["remainingKrw"]:
        fail(errors, "daily budget arithmetic must reconcile")
    checks += 2

    demo = load_example("demo-timeline-response.json")
    expected_months = ["2026-08", "2026-09", "2026-10", "2026-11", "2026-12", "2027-01"]
    frames = demo["frames"]
    if [frame["month"] for frame in frames] != expected_months:
        fail(errors, "demo frames must advance from August 2026 through January 2027")
    if len(frames) != 6 or any(frame["savingEventKrw"] != 500000 for frame in frames):
        fail(errors, "demo must contain exactly six 500,000 KRW saving events")
    cumulative = demo["initialGoalAmountKrw"]
    for frame in frames:
        cumulative += frame["savingEventKrw"]
        if frame["goalCurrentAmountKrw"] != cumulative:
            fail(errors, f"demo cumulative amount does not reconcile at {frame['month']}")
    if cumulative != demo["targetGoalAmountKrw"] or demo["mainGoal"]["state"] != "COMPLETED":
        fail(errors, "demo must finish at 5,000,000 KRW with a completed goal")
    checks += 4
    return checks


def resolve_parameter(spec: dict[str, Any], value: dict[str, Any]) -> dict[str, Any]:
    return resolve_component(spec, "parameters", value)


def check_cookie_header(
    spec: dict[str, Any],
    value: dict[str, Any] | None,
    expected: dict[str, Any],
    label: str,
    errors: list[str],
) -> None:
    if value is None:
        fail(errors, f"{label} must declare Set-Cookie")
        return
    header = resolve_component(spec, "headers", value)
    for key, expected_value in expected.items():
        if header.get(key) != expected_value:
            fail(errors, f"{label} Set-Cookie must declare {key}={expected_value!r}")
    pattern = header.get("schema", {}).get("pattern")
    example = header.get("example")
    if not pattern or not isinstance(example, str) or re.fullmatch(pattern, example) is None:
        fail(errors, f"{label} Set-Cookie example must satisfy its attribute pattern")


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
        fail(errors, "AuthSession must require accessToken, tokenType, expiresAt and user")
    if "refreshToken" in session.get("properties", {}):
        fail(errors, "AuthSession must never expose a refresh token")

    found = operations(spec)
    for operation_id in ("refreshSession", "logOut"):
        operation = found[operation_id][2]
        if "requestBody" in operation:
            fail(errors, f"{operation_id} must not accept a JSON refresh token body")
        parameters = [resolve_parameter(spec, item) for item in operation.get("parameters", [])]
        cookies = [item for item in parameters if item.get("in") == "cookie" and item.get("name") == "finmate_refresh"]
        if len(cookies) != 1:
            fail(errors, f"{operation_id} must consume the finmate_refresh cookie")

    cookie = {
        "x-cookie-name": "finmate_refresh",
        "x-http-only": True,
        "x-same-site": "Lax",
        "x-path": "/api/v1/auth",
        "x-max-age-seconds": 2592000,
    }
    for operation_id, status in (("signUp", "201"), ("logIn", "200"), ("refreshSession", "200")):
        response = resolve_component(spec, "responses", found[operation_id][2]["responses"][status])
        check_cookie_header(spec, response.get("headers", {}).get("Set-Cookie"), cookie, operation_id, errors)
    logout = resolve_component(spec, "responses", found["logOut"][2]["responses"]["204"])
    check_cookie_header(
        spec,
        logout.get("headers", {}).get("Set-Cookie"),
        {**cookie, "x-max-age-seconds": 0, "x-expires-immediately": True},
        "logOut",
        errors,
    )
    return 14


def check_goal_contract(spec: dict[str, Any], errors: list[str]) -> int:
    schemas = spec["components"]["schemas"]
    draft = schemas["UserGoalDraft"]
    if "currentAmountKrw" not in draft.get("required", []):
        fail(errors, "UserGoalDraft must require currentAmountKrw")
    if draft.get("properties", {}).get("title", {}).get("maxLength") != 255:
        fail(errors, "UserGoalDraft title must have maxLength 255")
    onboarding = schemas["CompleteOnboardingRequest"]
    if {"mainGoal", "goal", "confirmMainGoal"} & set(onboarding.get("properties", {})):
        fail(errors, "onboarding contract must not embed goal confirmation")
    states = set(schemas["OnboardingState"].get("enum", []))
    if states != {"EXPLORE_ONLY", "GOAL_ACTIVE"}:
        fail(errors, "OnboardingState must distinguish EXPLORE_ONLY and GOAL_ACTIVE")
    codes = set(schemas["Problem"]["properties"]["code"]["enum"])
    if not {"ACTIVE_MAIN_GOAL_EXISTS", "GOAL_REQUIRED", "VALIDATION_FAILED", "NOT_FOUND"} <= codes:
        fail(errors, "Problem codes must cover active-goal conflict, goal-required, validation and not-found")
    return 5


def check_operations(spec: dict[str, Any], errors: list[str]) -> None:
    found = operations(spec)
    missing = REQUIRED_OPERATIONS - set(found)
    if missing:
        fail(errors, f"missing required operationIds: {sorted(missing)}")
    forbidden = {"chooseRoutineAdaptationDomain", "createRoutineAdaptation"} & set(found)
    if forbidden:
        fail(errors, f"obsolete forced-choice operations remain: {sorted(forbidden)}")
    if ("POST", "/demo/timeline/advance") != found.get("advanceDemoTimeline", (None, None, {}))[:2]:
        fail(errors, "demo advancement must be POST /demo/timeline/advance")
    if "post" in spec.get("paths", {}).get("/hana-products/{productId}", {}):
        fail(errors, "Hana product endpoint must not offer enrollment")
    goal_guarded = {"acceptQuest", "getRelatedHanaProductInfo"}
    for operation_id in goal_guarded:
        response = found.get(operation_id, (None, None, {}))[2].get("responses", {}).get("409", {})
        if response.get("$ref") != "#/components/responses/GoalRequired":
            fail(errors, f"{operation_id} must declare the GoalRequired 409 response")
    routine_conflict = found.get("createRoutineRecommendation", (None, None, {}))[2].get("responses", {}).get("409", {})
    if routine_conflict.get("$ref") != "#/components/responses/RoutineRecommendationConflict":
        fail(errors, "createRoutineRecommendation must expose goal-required and stale-data conflicts")
    for operation_id, (_, _, operation) in found.items():
        responses = operation.get("responses", {})
        default = responses.get("default", {})
        has_default_problem = default.get("$ref") == "#/components/responses/ProblemResponse"
        if not has_default_problem and not any(str(code).startswith(("4", "5")) for code in responses):
            fail(errors, f"{operation_id} must declare an RFC 7807 error response")


def check_examples(spec: dict[str, Any], errors: list[str]) -> int:
    files = {path.name for path in (API_DIR / "examples").glob("*.json")}
    missing = set(EXAMPLE_SCHEMAS) - files
    unknown = files - set(EXAMPLE_SCHEMAS)
    if missing:
        fail(errors, f"missing mapped examples: {sorted(missing)}")
    if unknown:
        fail(errors, f"examples have no schema mapping: {sorted(unknown)}")
    for name, schema_name in EXAMPLE_SCHEMAS.items():
        try:
            payload = load_example(name)
        except json.JSONDecodeError as exc:
            fail(errors, f"invalid JSON example {name}: {exc}")
            continue
        expect_valid(schema_validator(spec, schema_name), payload, f"example {name}", errors)
    return len(files)


def check_docs(errors: list[str]) -> None:
    current_docs = sorted(
        path for path in VNEXT_DIR.rglob("*") if path.is_file() and path.suffix in {".md", ".yaml", ".yml"}
    )
    combined = "\n".join(path.read_text(encoding="utf-8") for path in current_docs)
    required = {
        "gaga-studio/finmate-api",
        "gaga-studio/finmate-web",
        "Java 21",
        "PostgreSQL",
        "React",
        "TypeScript",
        "Vite PWA",
        "EXPLORE_ONLY",
        "GOAL_ACTIVE",
        "홈",
        "메이트",
        "퀘스트",
        "기록",
        "SYNTHETIC",
        "DETERMINISTIC_APPROVED_COPY",
    }
    for phrase in sorted(required):
        if phrase not in combined:
            fail(errors, f"canonical docs missing binding phrase: {phrase}")
    forbidden = {
        r"FinRoom": "FinRoom",
        r"chooseRoutineAdaptationDomain": "obsolete forced-choice operation",
        r"RoutineAdaptationSet": "obsolete forced-choice schema",
        r"투자 공격력": "investment attack stat",
        r"하단 탭.{0,40}로드맵": "roadmap bottom tab",
    }
    for pattern, label in forbidden.items():
        if re.search(pattern, combined, re.IGNORECASE | re.DOTALL):
            fail(errors, f"docs/vnext retains forbidden active decision: {label}")
    archive_readme = LEGACY_DIR / "README.md"
    if not archive_readme.exists() or "ARCHIVED" not in archive_readme.read_text(encoding="utf-8"):
        fail(errors, "pre-RPG artifacts must remain in an ARCHIVED legacy package")


def main() -> int:
    errors: list[str] = []
    spec = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    try:
        validate_spec(spec)
    except Exception as exc:
        fail(errors, f"OpenAPI validation failed: {exc}")
    check_schema_contract(spec, errors)
    structural_checks = 0
    structural_checks += check_routine_contract(spec, errors)
    structural_checks += check_group_contract(spec, errors)
    structural_checks += check_product_flows(errors)
    structural_checks += check_record_and_demo(errors)
    check_operations(spec, errors)
    auth_checks = check_auth_contract(spec, errors)
    goal_checks = check_goal_contract(spec, errors)
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
        f"examples={example_count} structuralChecks={structural_checks} "
        f"authChecks={auth_checks} goalChecks={goal_checks}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
