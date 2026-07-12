#!/usr/bin/env python3
"""Verify the canonical FinMate vNext documentation and OpenAPI contract."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

import yaml
from openapi_spec_validator import validate_spec


API_DIR = Path(__file__).resolve().parent
VNEXT_DIR = API_DIR.parent
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
    r"LLM runtime": "LLM runtime",
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
            json.loads(path.read_text(encoding="utf-8"))
            example_count += 1
        except json.JSONDecodeError as exc:
            fail(errors, f"invalid JSON example {path.name}: {exc}")
    if example_count < 9:
        fail(errors, "at least nine endpoint examples are required")
    return example_count


def check_docs(errors: list[str]) -> None:
    canonical = [
        VNEXT_DIR / "README.md",
        VNEXT_DIR / "00-governance" / "decision-log.md",
        VNEXT_DIR / "00-governance" / "requirements-traceability.md",
        VNEXT_DIR / "01-product" / "prd.md",
        VNEXT_DIR / "03-domain" / "domain-model.md",
        VNEXT_DIR / "05-architecture" / "adr-001-stack-and-repository.md",
        VNEXT_DIR / "06-api" / "api-conventions.md",
    ]
    combined = "\n".join(path.read_text(encoding="utf-8") for path in canonical if path.exists())
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
            fail(errors, f"canonical docs retain forbidden active decision: {label}")


def main() -> int:
    errors: list[str] = []
    spec = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    try:
        validate_spec(spec)
    except Exception as exc:  # validator provides the actionable path
        fail(errors, f"OpenAPI validation failed: {exc}")
    check_schema_contract(spec, errors)
    check_operations(spec, errors)
    example_count = check_examples(spec, errors)
    check_docs(errors)
    if errors:
        print("CONTRACT_VERIFICATION_FAILED")
        for error in errors:
            print(f"- {error}")
        return 1
    print(
        "CONTRACT_VERIFICATION_OK "
        f"operations={len(operations(spec))} schemas={len(spec['components']['schemas'])} examples={example_count}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
