#!/usr/bin/env python3
"""Verify the integrated FinMate vNext domain, data, and API contracts."""

from __future__ import annotations

import copy
import json
import re
import sys
import warnings
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

import yaml

# RefResolver warns during import; the pinned OAS 3.0 validator still requires it.
warnings.filterwarnings("ignore", category=DeprecationWarning)
from jsonschema import FormatChecker, RefResolver  # noqa: E402
from openapi_schema_validator import OAS30Validator  # noqa: E402
from openapi_spec_validator import validate_spec  # noqa: E402


API_DIR = Path(__file__).resolve().parent
VNEXT_DIR = API_DIR.parent
OPENAPI_PATH = API_DIR / "openapi.yaml"
EXAMPLE_DIR = API_DIR / "examples"
HTTP_METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
DATA_DERIVED_OPERATIONS = {
    "getMyDataConnection",
    "createMyDataConnection",
    "revokeMyDataConnection",
    "requestMyDataSync",
    "getMyDataSync",
    "getFinancialBaseline",
    "listTransactionReviews",
    "classifyTransaction",
    "getHome",
    "listAdventurers",
    "getAdventurer",
    "createGoalCandidateSet",
    "getGoalCandidateSet",
    "createGoalReplacementCandidateSet",
    "confirmGoalReplacement",
    "createGoal",
    "getActiveGoal",
    "getGoal",
    "pauseGoal",
    "resumeGoal",
    "cancelGoal",
    "listGoalHistory",
    "listQuests",
    "getQuest",
    "startQuest",
    "completeQuest",
    "cancelQuest",
    "getAnimalReport",
    "listJourneyRecords",
    "getDailyRecord",
    "saveDailyReflection",
}

SYNC_JOB_STATUSES = (
    "REQUESTED",
    "FETCHING",
    "NORMALIZING",
    "RECONCILING",
    "SUCCEEDED",
    "PARTIAL_FAILED",
    "FAILED",
    "BLOCKED",
)
SYNC_TERMINAL_STATUSES = {"SUCCEEDED", "PARTIAL_FAILED", "FAILED", "BLOCKED"}
RECOMMENDATION_PROPERTIES = (
    "recommendedCandidateId",
    "reasonCodes",
    "summary",
    "riskNotice",
    "recommendationState",
)
RECOMMENDATION_STATES = {"AI_GENERATED", "DETERMINISTIC_FALLBACK"}
PUBLIC_API_PAYLOAD_SHAPE = {
    "successBody": "DOMAIN_PAYLOAD",
    "dataDerivedMetadata": ["dataFreshness"],
    "forbiddenTopLevelFields": ["result"],
    "calculationEnvelopeScope": "INTERNAL_FIXTURES_ONLY",
}

CALCULATION_ENVELOPE_FIELDS = (
    "calculationVersion",
    "sourceDataVersion",
    "dataState",
    "lastSyncedAt",
    "calculatedAt",
    "result",
)
EXPECTED_SCENARIO_ENVELOPES = {f"S{index:02d}": 1 for index in range(1, 19)}
EXPECTED_SCENARIO_ENVELOPES.update({"S05": 2, "S07": 4})
DATA_STATES = {"FRESH", "PENDING", "STALE", "INSUFFICIENT", "NEEDS_REVIEW"}
CORE_READ_STATE_COVERAGE = {
    "getFinancialBaseline": {"STALE", "INSUFFICIENT"},
    "getHome": {"STALE", "INSUFFICIENT"},
    "getActiveGoal": {"STALE", "INSUFFICIENT"},
    "getGoal": {"STALE", "INSUFFICIENT"},
    "getAnimalReport": {"STALE", "INSUFFICIENT"},
    "listAdventurers": {"STALE", "INSUFFICIENT"},
    # An insufficient recommendation set has no card that can be read by ID.
    "getAdventurer": {"STALE"},
}
GOAL_MUTATION_CONTRACTS = {
    "pauseGoal": (
        "#/components/responses/GoalPaused",
        "#/components/schemas/PauseGoalResponse",
        "PAUSED",
        "PAUSED",
    ),
    "resumeGoal": (
        "#/components/responses/GoalResumed",
        "#/components/schemas/ResumeGoalResponse",
        "ACTIVE",
        "RESUMED",
    ),
    "cancelGoal": (
        "#/components/responses/GoalCancelled",
        "#/components/schemas/CancelGoalResponse",
        "CANCELLED",
        "CANCELLED",
    ),
}
METRIC_RANGE_CONTRACTS = {
    "KRW": ("KrwMetricRange", "minKrw", "maxKrw"),
    "COUNT": ("CountMetricRange", "minCount", "maxCount"),
    "BASIS_POINT": ("BasisPointMetricRange", "minBps", "maxBps"),
}
CHALLENGE_VERIFICATION_SOURCES = {
    "APP_RISK_PROFILE_EVENT",
    "APP_DIVERSIFICATION_LEARNING_EVENT",
    "APP_CONCENTRATION_EXPLANATION_EVENT",
}
CANONICAL_TRANSACTION_FIELDS = (
    "transactionId",
    "userId",
    "accountId",
    "sourceSyncRunId",
    "providerId",
    "providerTransactionIdHash",
    "deduplicationKey",
    "bookedAt",
    "timePrecision",
    "amountKrw",
    "currency",
    "direction",
    "normalizedDescription",
    "merchantKeyHash",
    "classification",
    "classificationConfidenceBps",
    "linkedTransactionId",
    "reviewReasonCodes",
    "providerCreatedAt",
    "providerUpdatedAt",
    "providerDataAsOf",
    "sourceReceivedAt",
    "sourceResponseId",
    "sourceRecordOrdinal",
    "sourcePayloadSha256",
    "canonicalRecordSha256",
    "sourceDataVersion",
    "canonicalizationVersion",
)
CANONICAL_TRANSACTION_CONTRACT = {
    "fields": list(CANONICAL_TRANSACTION_FIELDS),
    "deduplicationSerialization": {
        "format": "LP_UTF8_V1",
        "domainTag": "finmate-transaction-dedup-v1",
        "componentFrame": "<ASCII byte-length>:<raw UTF-8 bytes>",
        "nullFrame": "-1:",
        "withProviderTransactionIdFields": [
            "providerId",
            "accountId",
            "providerTransactionId",
        ],
        "withoutProviderTransactionIdFields": [
            "providerId",
            "accountId",
            "bookedAt",
            "amountKrw",
            "direction",
            "normalizedDescription",
        ],
    },
    "winnerOrder": [
        {
            "field": "providerUpdatedAt",
            "direction": "DESC",
            "nulls": "LAST",
            "comparison": "INSTANT",
        },
        {
            "field": "sourceReceivedAt",
            "direction": "DESC",
            "nulls": "FORBIDDEN",
            "comparison": "INSTANT",
        },
        {
            "field": "sourceResponseId",
            "direction": "ASC",
            "nulls": "FORBIDDEN",
            "comparison": "UNICODE_CODE_POINT",
        },
        {
            "field": "sourceRecordOrdinal",
            "direction": "ASC",
            "nulls": "FORBIDDEN",
            "comparison": "INTEGER",
        },
        {
            "field": "canonicalRecordSha256",
            "direction": "ASC",
            "nulls": "FORBIDDEN",
            "comparison": "LOWERCASE_HEX",
        },
    ],
}
CONTRACT_CODES = {
    "errorCodes": [
        "ACTIVE_GOAL_EXISTS",
        "AUTH_REQUIRED",
        "AUTH_TOKEN_INVALID",
        "BUSINESS_RULE_VIOLATION",
        "CANDIDATE_EXPIRED",
        "CONSENT_REQUIRED",
        "DATA_INSUFFICIENT",
        "DATA_STALE",
        "DEPENDENCY_UNAVAILABLE",
        "FORBIDDEN",
        "IDEMPOTENCY_CONFLICT",
        "INTERNAL_ERROR",
        "INVALID_CURSOR",
        "MALFORMED_REQUEST",
        "NUMERIC_OVERFLOW",
        "PRECONDITION_FAILED",
        "RATE_LIMITED",
        "RECALIBRATION_REQUIRED",
        "RESOURCE_NOT_FOUND",
        "VALIDATION_FAILED",
    ],
    "idempotencyConflictCode": "IDEMPOTENCY_CONFLICT",
    "recalibrationConflictCode": "RECALIBRATION_REQUIRED",
    "recalibrationReasons": [
        "BASELINE_CHANGED_2000_BPS_OR_MORE",
        "TEMPLATE_INACTIVE",
        "REQUIRED_CONSENT_CHANGED",
        "PAUSE_EXCEEDED_P30D",
    ],
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def parse_instant(value: Any) -> datetime:
    if not isinstance(value, str):
        raise ValueError(f"expected date-time string, got {value!r}")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError(f"date-time must include a UTC offset: {value}")
    return parsed


def iter_objects(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from iter_objects(child)
    elif isinstance(value, list):
        for child in value:
            yield from iter_objects(child)


def tagged_json_blocks(text: str, tag: str) -> list[Any]:
    pattern = re.compile(
        rf"```json[ \t]+{re.escape(tag)}[ \t]*\n(.*?)\n```",
        flags=re.DOTALL,
    )
    return [json.loads(match.group(1)) for match in pattern.finditer(text)]


def snake_to_lower_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


def local_ref(root: dict[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        raise ValueError(f"only local refs are supported: {ref}")
    value: Any = root
    for part in ref[2:].split("/"):
        value = value[part.replace("~1", "/").replace("~0", "~")]
    return value


def resolve(root: dict[str, Any], value: Any) -> Any:
    seen: set[str] = set()
    while isinstance(value, dict) and set(value) == {"$ref"}:
        ref = value["$ref"]
        if ref in seen:
            raise ValueError(f"cyclic ref: {ref}")
        seen.add(ref)
        value = local_ref(root, ref)
    return value


def operations(spec: dict[str, Any]) -> Iterable[tuple[str, str, dict[str, Any]]]:
    for path, path_item in spec["paths"].items():
        for method, operation in path_item.items():
            if method.lower() in HTTP_METHODS:
                yield method.upper(), path, operation


def walk_external_values(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        external = value.get("externalValue")
        if isinstance(external, str):
            yield external
        for child in value.values():
            yield from walk_external_values(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_external_values(child)


def iter_content_examples(media: dict[str, Any]) -> Iterable[tuple[str, Any]]:
    if "example" in media:
        yield "inline", media["example"]
    for name, example in media.get("examples", {}).items():
        if "externalValue" in example:
            path = (API_DIR / example["externalValue"]).resolve()
            yield str(path), json.loads(path.read_text(encoding="utf-8"))
        elif "value" in example:
            yield name, example["value"]


def validate_media_examples(
    spec: dict[str, Any],
    media: dict[str, Any],
    label: str,
    errors: list[str],
) -> list[Any]:
    schema = media.get("schema")
    examples: list[Any] = []
    if not schema:
        return examples
    validator = OAS30Validator(
        schema,
        resolver=RefResolver.from_schema(spec),
        format_checker=FormatChecker(),
    )
    for source, instance in iter_content_examples(media):
        examples.append(instance)
        for error in validator.iter_errors(instance):
            location = "/".join(str(part) for part in error.absolute_path) or "<root>"
            fail(errors, f"{label} example {source} at {location}: {error.message}")
    return examples


def schema_requires_property(
    spec: dict[str, Any],
    schema: dict[str, Any],
    property_name: str,
    seen: set[int] | None = None,
) -> bool:
    seen = seen or set()
    schema = resolve(spec, schema)
    marker = id(schema)
    if marker in seen:
        return False
    seen.add(marker)
    if property_name in schema.get("required", []) and property_name in schema.get(
        "properties", {}
    ):
        return True
    if "allOf" in schema:
        return any(
            schema_requires_property(spec, item, property_name, seen)
            for item in schema["allOf"]
        )
    if "oneOf" in schema:
        return bool(schema["oneOf"]) and all(
            schema_requires_property(spec, item, property_name, seen.copy())
            for item in schema["oneOf"]
        )
    return False


def schema_declares_property(
    spec: dict[str, Any],
    schema: dict[str, Any],
    property_name: str,
    seen: set[int] | None = None,
) -> bool:
    seen = seen or set()
    schema = resolve(spec, schema)
    marker = id(schema)
    if marker in seen:
        return False
    seen.add(marker)
    if property_name in schema.get("properties", {}):
        return True
    return any(
        schema_declares_property(spec, item, property_name, seen.copy())
        for composition in ("allOf", "oneOf", "anyOf")
        for item in schema.get(composition, [])
    )


def response_json_media(
    spec: dict[str, Any], response: dict[str, Any]
) -> dict[str, Any] | None:
    response = resolve(spec, response)
    content = response.get("content", {})
    return content.get("application/json") or content.get("application/problem+json")


def top_level_data_state(value: Any) -> str | None:
    if not isinstance(value, dict):
        return None
    freshness = value.get("dataFreshness")
    if isinstance(freshness, dict) and isinstance(freshness.get("state"), str):
        return freshness["state"]
    return None


def metric_range_error(value: Any, spec: dict[str, Any]) -> str | None:
    if not isinstance(value, dict):
        return None
    contract = METRIC_RANGE_CONTRACTS.get(value.get("unit"))
    if contract is None:
        return None
    schema_name, default_min, default_max = contract
    schema = spec["components"]["schemas"].get(schema_name, {})
    extension = schema.get("x-finmate-ordered-pair", {})
    min_property = extension.get("minProperty", default_min)
    max_property = extension.get("maxProperty", default_max)
    if min_property not in value and max_property not in value:
        return None
    if min_property not in value or max_property not in value:
        return f"must contain both {min_property} and {max_property}"
    minimum = value[min_property]
    maximum = value[max_property]
    if isinstance(minimum, bool) or not isinstance(minimum, int):
        return f"{min_property} must be an integer"
    if isinstance(maximum, bool) or not isinstance(maximum, int):
        return f"{max_property} must be an integer"
    if minimum > maximum:
        return f"{min_property}={minimum} exceeds {max_property}={maximum}"
    return None


def validate_metric_ranges(
    value: Any,
    spec: dict[str, Any],
    label: str,
    errors: list[str],
) -> int:
    checked = 0
    for candidate in iter_objects(value):
        contract = METRIC_RANGE_CONTRACTS.get(candidate.get("unit"))
        if contract is None:
            continue
        _, min_property, max_property = contract
        if min_property not in candidate and max_property not in candidate:
            continue
        checked += 1
        semantic_error = metric_range_error(candidate, spec)
        if semantic_error:
            fail(errors, f"{label} metric range: {semantic_error}")
    return checked


def check_metric_range_contracts(
    spec: dict[str, Any], errors: list[str]
) -> dict[str, int]:
    schemas = spec["components"]["schemas"]
    negative_count = 0
    positive_count = 0
    for unit, (
        schema_name,
        min_property,
        max_property,
    ) in METRIC_RANGE_CONTRACTS.items():
        schema = schemas.get(schema_name, {})
        expected_extension = {
            "minProperty": min_property,
            "maxProperty": max_property,
            "rule": "MIN_LE_MAX",
        }
        if schema.get("x-finmate-ordered-pair") != expected_extension:
            fail(
                errors,
                f"{schema_name} must declare x-finmate-ordered-pair {expected_extension}",
            )

        invalid = {"unit": unit, min_property: 2, max_property: 1}
        validator = OAS30Validator(
            schema,
            resolver=RefResolver.from_schema(spec),
            format_checker=FormatChecker(),
        )
        schema_errors = list(validator.iter_errors(invalid))
        if schema_errors:
            fail(
                errors,
                f"{schema_name} inverted-range negative fixture must pass OAS field validation before semantic validation",
            )
        if metric_range_error(invalid, spec) is None:
            fail(errors, f"{schema_name} inverted range was not rejected semantically")
        negative_count += 1

        valid = {"unit": unit, min_property: 1, max_property: 1}
        if list(validator.iter_errors(valid)) or metric_range_error(valid, spec):
            fail(errors, f"{schema_name} equal-boundary range must be accepted")
        positive_count += 1
    return {
        "metricRangeNegative": negative_count,
        "metricRangePositive": positive_count,
    }


def operation_parameter_names(
    spec: dict[str, Any], operation: dict[str, Any]
) -> set[str]:
    return {
        parameter.get("name")
        for item in operation.get("parameters", [])
        if isinstance((parameter := resolve(spec, item)), dict)
        and isinstance(parameter.get("name"), str)
    }


def privacy_mode_branches(schema: dict[str, Any]) -> dict[bool, dict[str, Any]]:
    """Return privacy oneOf branches keyed by their fixed opt-in value."""

    branches: dict[bool, dict[str, Any]] = {}
    for branch in schema.get("oneOf", []):
        properties = branch.get("properties", {})
        values = properties.get("anonymousCardOptIn", {}).get("enum", [])
        if len(values) == 1 and isinstance(values[0], bool):
            branches[values[0]] = properties
    return branches


def check_privacy_operation_contract(spec: dict[str, Any], errors: list[str]) -> None:
    op_by_id = {
        op.get("operationId"): (method, path, op)
        for method, path, op in operations(spec)
    }
    method, path, operation = op_by_id.get("savePrivacySettings", (None, None, {}))
    if (method, path) != ("PUT", "/me/privacy"):
        fail(errors, "savePrivacySettings must be PUT /me/privacy")
        return

    parameter_names = operation_parameter_names(spec, operation)
    for required_header in ("Idempotency-Key", "If-Match"):
        if required_header not in parameter_names:
            fail(errors, f"savePrivacySettings must require {required_header}")

    request_body = resolve(spec, operation.get("requestBody", {}))
    request_media = request_body.get("content", {}).get("application/json", {})
    if request_media.get("schema") != {
        "$ref": "#/components/schemas/SavePrivacySettingsRequest"
    }:
        fail(
            errors,
            "savePrivacySettings must use SavePrivacySettingsRequest",
        )
    request_example_names = set(request_media.get("examples", {}))
    if not {"withdrawal", "staleDelayedOptIn"}.issubset(request_example_names):
        fail(
            errors,
            "savePrivacySettings must example withdrawal and staleDelayedOptIn requests",
        )

    schemas = spec["components"]["schemas"]
    request_schema = schemas.get("SavePrivacySettingsRequest", {})
    request_required = set(request_schema.get("required", []))
    required_request_fields = {
        "consentAggregateId",
        "expectedVersion",
        "anonymousCardOptIn",
        "exposedFields",
        "consentVersion",
    }
    if not required_request_fields.issubset(request_required):
        fail(
            errors,
            "SavePrivacySettingsRequest must require aggregate, version, opt-in, fields, and consent version",
        )
    request_properties = request_schema.get("properties", {})
    if request_properties.get("consentAggregateId") != {
        "type": "string",
        "format": "uuid",
    }:
        fail(errors, "SavePrivacySettingsRequest.consentAggregateId must be a UUID")
    expected_version = request_properties.get("expectedVersion", {})
    if (
        expected_version.get("type") != "integer"
        or expected_version.get("minimum") != 1
    ):
        fail(
            errors,
            "SavePrivacySettingsRequest.expectedVersion must be an integer >= 1",
        )
    request_modes = privacy_mode_branches(request_schema)
    if len(request_schema.get("oneOf", [])) != 2 or set(request_modes) != {False, True}:
        fail(
            errors,
            "SavePrivacySettingsRequest must have exactly one opt-out and one opt-in branch",
        )
    else:
        if request_modes[False].get("exposedFields", {}).get("maxItems") != 0:
            fail(errors, "privacy opt-out request must expose no public fields")
        if request_modes[True].get("exposedFields", {}).get("minItems") != 1:
            fail(errors, "privacy opt-in request must expose at least one public field")

    responses = operation.get("responses", {})
    for required_status in ("200", "409", "412"):
        if required_status not in responses:
            fail(
                errors,
                f"savePrivacySettings must declare response {required_status}",
            )
    success_response = resolve(spec, responses.get("200", {}))
    success_headers = success_response.get("headers", {})
    for required_header in ("ETag", "Idempotency-Replayed", "X-Request-Id"):
        if required_header not in success_headers:
            fail(
                errors,
                f"savePrivacySettings response 200 must return {required_header}",
            )
    privacy_etag_ref = {"$ref": "#/components/headers/PrivacyETag"}
    if success_headers.get("ETag") != privacy_etag_ref:
        fail(errors, "savePrivacySettings response 200 must use PrivacyETag")
    success_media = response_json_media(spec, responses.get("200", {}))
    if success_media is None or success_media.get("schema") != {
        "$ref": "#/components/schemas/PrivacySettings"
    }:
        fail(errors, "savePrivacySettings response 200 must use PrivacySettings")
    elif "withdrawn" not in success_media.get("examples", {}):
        fail(errors, "savePrivacySettings must example the successful withdrawal")

    response_schema = schemas.get("PrivacySettings", {})
    response_required = set(response_schema.get("required", []))
    required_response_fields = {
        "consentAggregateId",
        "anonymousCardOptIn",
        "exposedFields",
        "consentVersion",
        "version",
        "updatedAt",
        "shareConsentState",
    }
    if not required_response_fields.issubset(response_required):
        fail(
            errors,
            "PrivacySettings must require the complete publication aggregate",
        )
    response_properties = response_schema.get("properties", {})
    consent_aggregate_id = response_properties.get("consentAggregateId", {})
    if (
        consent_aggregate_id.get("type") != "string"
        or consent_aggregate_id.get("format") != "uuid"
    ):
        fail(errors, "PrivacySettings.consentAggregateId must be a UUID")
    version = response_properties.get("version", {})
    if version.get("type") != "integer" or version.get("minimum") != 1:
        fail(errors, "PrivacySettings.version must be an integer >= 1")
    response_modes = privacy_mode_branches(response_schema)
    if len(response_schema.get("oneOf", [])) != 2 or set(response_modes) != {False, True}:
        fail(
            errors,
            "PrivacySettings must have exactly one opt-out and one opt-in branch",
        )
    else:
        opted_out_states = set(
            response_modes[False].get("shareConsentState", {}).get("enum", [])
        )
        opted_in_states = set(
            response_modes[True].get("shareConsentState", {}).get("enum", [])
        )
        if opted_out_states != {"OPTED_OUT", "WITHDRAWN"}:
            fail(errors, "privacy opt-out response must be OPTED_OUT or WITHDRAWN")
        if opted_in_states != {"ACTIVE", "UNDER_REVIEW"}:
            fail(errors, "privacy opt-in response must be ACTIVE or UNDER_REVIEW")
        if response_modes[False].get("exposedFields", {}).get("maxItems") != 0:
            fail(errors, "privacy opt-out response must expose no public fields")
        if response_modes[True].get("exposedFields", {}).get("minItems") != 1:
            fail(errors, "privacy opt-in response must expose at least one public field")

    stale_response = resolve(spec, responses.get("412", {}))
    for required_header in ("ETag", "X-Request-Id"):
        if required_header not in stale_response.get("headers", {}):
            fail(
                errors,
                f"savePrivacySettings response 412 must return {required_header}",
            )
    if stale_response.get("headers", {}).get("ETag") != privacy_etag_ref:
        fail(errors, "savePrivacySettings response 412 must use PrivacyETag")
    stale_media = response_json_media(spec, responses.get("412", {}))
    if stale_media is None or stale_media.get("schema") != {
        "$ref": "#/components/schemas/Problem"
    }:
        fail(errors, "savePrivacySettings response 412 must use Problem")
    elif "staleDelayedOptIn" not in stale_media.get("examples", {}):
        fail(
            errors,
            "savePrivacySettings response 412 must example the stale delayed opt-in",
        )


def check_privacy_contract_with_mutations(
    spec: dict[str, Any], errors: list[str]
) -> int:
    baseline_errors: list[str] = []
    check_privacy_operation_contract(spec, baseline_errors)
    errors.extend(baseline_errors)
    if baseline_errors:
        return 0

    def remove_parameter(document: dict[str, Any], name: str) -> None:
        operation = document["paths"]["/me/privacy"]["put"]
        operation["parameters"] = [
            item
            for item in operation["parameters"]
            if resolve(document, item).get("name") != name
        ]

    def remove_success_header(document: dict[str, Any], name: str) -> None:
        document["paths"]["/me/privacy"]["put"]["responses"]["200"]["headers"].pop(name)

    def remove_required_property(
        document: dict[str, Any], schema_name: str, property_name: str
    ) -> None:
        required = document["components"]["schemas"][schema_name]["required"]
        required.remove(property_name)

    def remove_stale_response(document: dict[str, Any]) -> None:
        document["paths"]["/me/privacy"]["put"]["responses"].pop("412")

    def remove_mode_constraint(document: dict[str, Any], schema_name: str) -> None:
        document["components"]["schemas"][schema_name].pop("oneOf")

    def add_response_escape_branch(document: dict[str, Any]) -> None:
        document["components"]["schemas"]["PrivacySettings"]["oneOf"].insert(
            0,
            {
                "title": "Invalid escape branch",
                "properties": {
                    "anonymousCardOptIn": {"enum": [False]},
                    "exposedFields": {"minItems": 1},
                    "shareConsentState": {"enum": ["ACTIVE"]},
                },
            },
        )

    def use_generic_etag(document: dict[str, Any], status: str) -> None:
        document["paths"]["/me/privacy"]["put"]["responses"][status]["headers"][
            "ETag"
        ] = {"$ref": "#/components/headers/ETag"}

    def change_property_type(
        document: dict[str, Any], schema_name: str, property_name: str
    ) -> None:
        document["components"]["schemas"][schema_name]["properties"][property_name][
            "type"
        ] = "string"

    mutations = (
        (
            "privacy missing Idempotency-Key",
            lambda document: remove_parameter(document, "Idempotency-Key"),
        ),
        (
            "privacy missing If-Match",
            lambda document: remove_parameter(document, "If-Match"),
        ),
        (
            "privacy missing ETag",
            lambda document: remove_success_header(document, "ETag"),
        ),
        (
            "privacy request missing expectedVersion",
            lambda document: remove_required_property(
                document, "SavePrivacySettingsRequest", "expectedVersion"
            ),
        ),
        (
            "privacy request missing consentAggregateId",
            lambda document: remove_required_property(
                document, "SavePrivacySettingsRequest", "consentAggregateId"
            ),
        ),
        (
            "privacy request missing exposedFields",
            lambda document: remove_required_property(
                document, "SavePrivacySettingsRequest", "exposedFields"
            ),
        ),
        (
            "privacy request expectedVersion changed to string",
            lambda document: change_property_type(
                document, "SavePrivacySettingsRequest", "expectedVersion"
            ),
        ),
        (
            "privacy response missing version",
            lambda document: remove_required_property(
                document, "PrivacySettings", "version"
            ),
        ),
        (
            "privacy response missing consentAggregateId",
            lambda document: remove_required_property(
                document, "PrivacySettings", "consentAggregateId"
            ),
        ),
        (
            "privacy response version changed to string",
            lambda document: change_property_type(
                document, "PrivacySettings", "version"
            ),
        ),
        (
            "privacy request missing publication mode constraint",
            lambda document: remove_mode_constraint(
                document, "SavePrivacySettingsRequest"
            ),
        ),
        (
            "privacy response missing publication mode constraint",
            lambda document: remove_mode_constraint(document, "PrivacySettings"),
        ),
        (
            "privacy response contains an opt-out escape branch",
            add_response_escape_branch,
        ),
        (
            "privacy success response uses generic ETag",
            lambda document: use_generic_etag(document, "200"),
        ),
        (
            "privacy stale response uses generic ETag",
            lambda document: use_generic_etag(document, "412"),
        ),
        ("privacy missing 412", remove_stale_response),
    )
    for label, mutate in mutations:
        mutated = copy.deepcopy(spec)
        mutate(mutated)
        mutation_errors: list[str] = []
        check_privacy_operation_contract(mutated, mutation_errors)
        if not mutation_errors:
            fail(errors, f"contract mutation survived: {label}")
    return len(mutations)


def check_quest_cancel_contract(spec: dict[str, Any], errors: list[str]) -> int:
    op_by_id = {
        op.get("operationId"): (method, path, op)
        for method, path, op in operations(spec)
    }
    method, path, operation = op_by_id.get("cancelQuest", (None, None, {}))
    if (method, path) != ("POST", "/quests/{questId}/cancel"):
        fail(errors, "cancelQuest must be POST /quests/{questId}/cancel")
        return 0

    parameter_names = operation_parameter_names(spec, operation)
    for required_parameter in ("questId", "Idempotency-Key", "If-Match"):
        if required_parameter not in parameter_names:
            fail(errors, f"cancelQuest must require {required_parameter}")

    request_body = resolve(spec, operation.get("requestBody", {}))
    request_media = request_body.get("content", {}).get("application/json", {})
    if request_media.get("schema") != {
        "$ref": "#/components/schemas/CancelQuestRequest"
    }:
        fail(errors, "cancelQuest must use CancelQuestRequest")
    request_examples = list(iter_content_examples(request_media))
    if not request_examples:
        fail(errors, "cancelQuest must have a request example")
    request_reasons = {
        example.get("reason")
        for _, example in request_examples
        if isinstance(example, dict)
    }

    responses = operation.get("responses", {})
    if responses.get("200") != {"$ref": "#/components/responses/QuestCancelled"}:
        fail(errors, "cancelQuest must use the QuestCancelled response")
    for required_status in ("400", "401", "404", "409", "412", "429", "500"):
        if required_status not in responses:
            fail(errors, f"cancelQuest missing error response {required_status}")

    response = resolve(spec, responses.get("200", {}))
    for required_header in (
        "ETag",
        "Idempotency-Replayed",
        "X-Data-State",
        "X-Request-Id",
    ):
        if required_header not in response.get("headers", {}):
            fail(errors, f"cancelQuest response 200 must return {required_header}")
    media = response_json_media(spec, response)
    if media is None or media.get("schema") != {
        "$ref": "#/components/schemas/CancelQuestResponse"
    }:
        fail(errors, "cancelQuest must advertise CancelQuestResponse")
        return 0

    examples = list(iter_content_examples(media))
    if not examples:
        fail(errors, "cancelQuest must have an operation-specific response example")
        return 0
    validator = OAS30Validator(
        media["schema"],
        resolver=RefResolver.from_schema(spec),
        format_checker=FormatChecker(),
    )
    for source, example in examples:
        if not isinstance(example, dict):
            fail(errors, f"cancelQuest example {source} must be an object")
            continue
        if example.get("status") != "CANCELLED" or not example.get(
            "cancellationReason"
        ):
            fail(
                errors,
                f"cancelQuest example {source} must return CANCELLED with a reason",
            )
            continue
        if example.get("cancellationReason") not in request_reasons:
            fail(
                errors,
                f"cancelQuest example {source} reason must match its request fixture",
            )
        invalid_status = copy.deepcopy(example)
        invalid_status["status"] = "IN_PROGRESS"
        if not list(validator.iter_errors(invalid_status)):
            fail(errors, "CancelQuestResponse accepts a non-CANCELLED status")
        invalid_reason = copy.deepcopy(example)
        invalid_reason["cancellationReason"] = None
        if not list(validator.iter_errors(invalid_reason)):
            fail(errors, "CancelQuestResponse accepts a null cancellationReason")
    return 1


def check_sync_job_contract(spec: dict[str, Any], errors: list[str]) -> dict[str, int]:
    schema = spec["components"]["schemas"].get("SyncJob", {})
    actual_statuses = tuple(
        schema.get("properties", {}).get("status", {}).get("enum", [])
    )
    if actual_statuses != SYNC_JOB_STATUSES:
        fail(
            errors,
            f"SyncJob.status must contain per-run states only: {list(SYNC_JOB_STATUSES)}",
        )

    op_by_id = {op.get("operationId"): op for _, _, op in operations(spec)}
    operation = op_by_id.get("getMyDataSync", {})
    media = response_json_media(spec, operation.get("responses", {}).get("200", {}))
    statuses: set[str] = set()
    idle_mutation_checked = 0
    if media is not None:
        examples = list(iter_content_examples(media))
        for source, example in examples:
            if not isinstance(example, dict):
                continue
            status = example.get("status")
            if isinstance(status, str):
                statuses.add(status)
            if status == "IDLE":
                fail(errors, f"getMyDataSync example {source} exposes IDLE as a run")
        if examples:
            _, example = examples[0]
            if isinstance(example, dict):
                invalid_idle = copy.deepcopy(example)
                invalid_idle["status"] = "IDLE"
                validator = OAS30Validator(
                    media["schema"],
                    resolver=RefResolver.from_schema(spec),
                    format_checker=FormatChecker(),
                )
                if not list(validator.iter_errors(invalid_idle)):
                    fail(errors, "SyncJob schema accepts the forbidden IDLE run status")
                idle_mutation_checked = 1
    required_terminal_examples = {"SUCCEEDED", "PARTIAL_FAILED"}
    if not required_terminal_examples.issubset(statuses):
        fail(
            errors,
            "getMyDataSync must example queryable SUCCEEDED and PARTIAL_FAILED terminal runs",
        )
    return {
        "syncStatuses": len(actual_statuses),
        "syncTerminalExamples": len(statuses & SYNC_TERMINAL_STATUSES),
        "syncIdleMutations": idle_mutation_checked,
    }


def candidate_recommendation_errors(candidate_set: Any) -> list[str]:
    if not isinstance(candidate_set, dict):
        return ["candidate set must be an object"]
    candidates = candidate_set.get("candidates")
    recommendation = candidate_set.get("recommendation")
    if not isinstance(candidates, list) or not isinstance(recommendation, dict):
        return ["candidate set must contain candidates and recommendation"]

    violations: list[str] = []
    if set(recommendation) != set(RECOMMENDATION_PROPERTIES):
        violations.append(
            f"recommendation properties must be {list(RECOMMENDATION_PROPERTIES)}"
        )
    candidate_ids = {
        candidate.get("candidateId")
        for candidate in candidates
        if isinstance(candidate, dict)
    }
    if recommendation.get("recommendedCandidateId") not in candidate_ids:
        violations.append("recommendedCandidateId is outside the candidate set")
    if recommendation.get("recommendationState") not in RECOMMENDATION_STATES:
        violations.append("recommendationState is invalid")
    for property_name in ("summary", "riskNotice"):
        value = recommendation.get(property_name)
        if not isinstance(value, str) or re.search(r"[0-9]", value):
            violations.append(
                f"{property_name} must not repeat or invent numeric values"
            )
    stack = [recommendation]
    while stack:
        value = stack.pop()
        if isinstance(value, dict):
            stack.extend(value.values())
        elif isinstance(value, list):
            stack.extend(value)
        elif isinstance(value, (int, float)) and not isinstance(value, bool):
            violations.append("recommendation must not carry numeric values")
            break
    return violations


def check_candidate_recommendation_contract(
    spec: dict[str, Any], errors: list[str]
) -> dict[str, int]:
    schemas = spec["components"]["schemas"]
    recommendation_schema = schemas.get("GoalCandidateRecommendation", {})
    if recommendation_schema.get("additionalProperties") is not False:
        fail(errors, "GoalCandidateRecommendation must forbid additional properties")
    if set(recommendation_schema.get("required", [])) != set(RECOMMENDATION_PROPERTIES):
        fail(
            errors,
            "GoalCandidateRecommendation must require only the structured recommendation fields",
        )
    properties = recommendation_schema.get("properties", {})
    if set(properties) != set(RECOMMENDATION_PROPERTIES):
        fail(
            errors,
            "GoalCandidateRecommendation must not expose code-owned candidate values",
        )
    state_values = set(properties.get("recommendationState", {}).get("enum", []))
    if state_values != RECOMMENDATION_STATES:
        fail(
            errors,
            "GoalCandidateRecommendation.recommendationState must distinguish AI and fallback",
        )
    for property_name in ("summary", "riskNotice"):
        if properties.get(property_name, {}).get("pattern") != "^[^0-9]*$":
            fail(
                errors,
                f"GoalCandidateRecommendation.{property_name} must reject numeric text",
            )

    expected_membership = {
        "memberPath": "recommendation.recommendedCandidateId",
        "setPath": "candidates[].candidateId",
    }
    for schema_name in ("NewGoalCandidateSet", "ReplacementGoalCandidateSet"):
        candidate_set_schema = schemas.get(schema_name, {})
        if "recommendation" not in candidate_set_schema.get("required", []):
            fail(errors, f"{schema_name} must require recommendation")
        if candidate_set_schema.get("properties", {}).get("recommendation") != {
            "$ref": "#/components/schemas/GoalCandidateRecommendation"
        }:
            fail(
                errors,
                f"{schema_name}.recommendation must use GoalCandidateRecommendation",
            )
        if candidate_set_schema.get("x-finmate-id-membership") != expected_membership:
            fail(
                errors,
                f"{schema_name} must declare recommendation candidate ID membership",
            )

    op_by_id = {op.get("operationId"): op for _, _, op in operations(spec)}
    checked_examples: list[dict[str, Any]] = []
    states: set[str] = set()
    purposes: set[str] = set()
    for operation_id in (
        "createGoalCandidateSet",
        "getGoalCandidateSet",
        "createGoalReplacementCandidateSet",
    ):
        operation = op_by_id.get(operation_id, {})
        for status, response in operation.get("responses", {}).items():
            if not str(status).startswith("2"):
                continue
            media = response_json_media(spec, response)
            if media is None:
                continue
            for source, example in iter_content_examples(media):
                violations = candidate_recommendation_errors(example)
                for violation in violations:
                    fail(
                        errors,
                        f"{operation_id} example {source} recommendation: {violation}",
                    )
                if isinstance(example, dict) and not violations:
                    checked_examples.append(example)
                    recommendation = example["recommendation"]
                    states.add(recommendation["recommendationState"])
                    purpose = example.get("purpose")
                    if isinstance(purpose, str):
                        purposes.add(purpose)
    if states != RECOMMENDATION_STATES:
        fail(
            errors,
            f"candidate set examples must cover AI and deterministic fallback: {sorted(states)}",
        )
    if purposes != {"NEW", "REPLACEMENT"}:
        fail(
            errors,
            f"candidate set recommendation examples must cover both purposes: {sorted(purposes)}",
        )

    mutation_count = 0
    if checked_examples:
        example = checked_examples[0]
        semantic_mutations = (
            (
                "unknown candidate ID",
                lambda value: value["recommendation"].__setitem__(
                    "recommendedCandidateId",
                    "ffffffff-ffff-4fff-8fff-ffffffffffff",
                ),
            ),
            (
                "numeric recommendation property",
                lambda value: value["recommendation"].__setitem__(
                    "targetValueBps", 9999
                ),
            ),
            (
                "numeric recommendation summary",
                lambda value: value["recommendation"].__setitem__(
                    "summary", value["recommendation"]["summary"] + " 9999"
                ),
            ),
        )
        for label, mutate in semantic_mutations:
            invalid = copy.deepcopy(example)
            mutate(invalid)
            if not candidate_recommendation_errors(invalid):
                fail(errors, f"candidate recommendation mutation survived: {label}")
            mutation_count += 1

        if recommendation_schema:
            validator = OAS30Validator(
                recommendation_schema,
                resolver=RefResolver.from_schema(spec),
                format_checker=FormatChecker(),
            )
            invalid_recommendation = copy.deepcopy(example["recommendation"])
            invalid_recommendation["targetValueBps"] = 9999
            if not list(validator.iter_errors(invalid_recommendation)):
                fail(
                    errors,
                    "GoalCandidateRecommendation schema accepts a numeric candidate mutation",
                )
    return {
        "candidateRecommendations": len(checked_examples),
        "recommendationMutations": mutation_count,
    }


def check_openapi(spec: dict[str, Any], errors: list[str]) -> dict[str, Any]:
    try:
        validate_spec(spec)
    except Exception as exc:  # validator supplies the actionable path
        fail(errors, f"OpenAPI validation failed: {exc}")

    schemas = spec["components"]["schemas"]
    metric_stats = check_metric_range_contracts(spec, errors)
    if spec.get("x-finmate-contract-codes") != CONTRACT_CODES:
        fail(
            errors,
            "OpenAPI x-finmate-contract-codes must match the executable registry",
        )
    if schemas.get("ProblemCode", {}).get("enum") != CONTRACT_CODES["errorCodes"]:
        fail(errors, "ProblemCode enum must match the canonical error code registry")
    recalibration_reason_values = schemas.get("RecalibrationReason", {}).get("enum", [])
    if [
        value for value in recalibration_reason_values if value is not None
    ] != CONTRACT_CODES["recalibrationReasons"]:
        fail(
            errors, "RecalibrationReason enum must match the canonical reason registry"
        )
    if schemas.get("Problem", {}).get("properties", {}).get("code") != {
        "$ref": "#/components/schemas/ProblemCode"
    }:
        fail(errors, "Problem.code must reference ProblemCode")
    if schemas.get("Recalibration", {}).get("properties", {}).get("reason") != {
        "$ref": "#/components/schemas/RecalibrationReason"
    }:
        fail(errors, "Recalibration.reason must reference RecalibrationReason")

    security = spec["components"].get("securitySchemes", {})
    refresh = security.get("RefreshCookie")
    if refresh != {
        "type": "apiKey",
        "in": "cookie",
        "name": "finmate_refresh",
        "description": "HttpOnly Secure SameSite=Lax refresh cookie",
    }:
        fail(
            errors,
            "RefreshCookie must be the declared finmate_refresh cookie security scheme",
        )

    op_by_id = {
        op["operationId"]: (method, path, op) for method, path, op in operations(spec)
    }
    missing_ops = DATA_DERIVED_OPERATIONS - set(op_by_id)
    if missing_ops:
        fail(errors, f"missing required operations: {sorted(missing_ops)}")

    privacy_mutation_count = check_privacy_contract_with_mutations(spec, errors)
    quest_cancel_count = check_quest_cancel_contract(spec, errors)
    sync_stats = check_sync_job_contract(spec, errors)
    recommendation_stats = check_candidate_recommendation_contract(spec, errors)

    refresh_op = op_by_id.get("refreshSession", (None, None, {}))[2]
    if refresh_op.get("security") != [{"RefreshCookie": []}]:
        fail(errors, "refreshSession must require RefreshCookie")
    logout_op = op_by_id.get("logOut", (None, None, {}))[2]
    if logout_op.get("security") != [{"bearerAuth": [], "RefreshCookie": []}]:
        fail(errors, "logOut must require bearerAuth and RefreshCookie together")

    signup = op_by_id.get("signUp", (None, None, {}))[2]
    signup_parameters = [
        resolve(spec, item).get("name") for item in signup.get("parameters", [])
    ]
    if "Idempotency-Key" not in signup_parameters:
        fail(errors, "signUp must require Idempotency-Key")
    if "200" in signup.get("responses", {}) or "201" not in signup.get("responses", {}):
        fail(errors, "signUp replay contract must use the original 201 response only")

    for operation_id in ("getHome", "getGoal", "getDailyRecord"):
        operation = op_by_id.get(operation_id, (None, None, {}))[2]
        parameter_names = [
            resolve(spec, item).get("name") for item in operation.get("parameters", [])
        ]
        if "If-None-Match" not in parameter_names or "304" not in operation.get(
            "responses", {}
        ):
            fail(errors, f"{operation_id} must declare If-None-Match and 304")

    operation_success_states: dict[str, set[str]] = {}
    response_example_count = 0
    metric_range_examples = 0
    direct_payload_count = 0
    for method, path, operation in operations(spec):
        operation_id = operation["operationId"]
        responses = operation.get("responses", {})
        for required_status in ("429", "500"):
            if required_status not in responses:
                fail(
                    errors,
                    f"{operation_id} missing standard {required_status} response",
                )
        for status, raw_response in responses.items():
            response = resolve(spec, raw_response)
            if "X-Request-Id" not in response.get("headers", {}):
                fail(errors, f"{operation_id} response {status} missing X-Request-Id")
            media = response_json_media(spec, raw_response)
            if not media:
                continue
            examples = validate_media_examples(
                spec, media, f"{method} {path} response {status}", errors
            )
            if str(status).startswith("2"):
                if not examples:
                    fail(
                        errors,
                        f"{operation_id} response {status} missing success example",
                    )
                response_example_count += len(examples)
                for example in examples:
                    state = top_level_data_state(example)
                    if state is not None:
                        operation_success_states.setdefault(operation_id, set()).add(
                            state
                        )
                    metric_range_examples += validate_metric_ranges(
                        example,
                        spec,
                        f"{method} {path} response {status}",
                        errors,
                    )
        request_body = resolve(spec, operation.get("requestBody", {}))
        for content_type, media in request_body.get("content", {}).items():
            request_examples = validate_media_examples(
                spec, media, f"{method} {path} request {content_type}", errors
            )
            for example in request_examples:
                metric_range_examples += validate_metric_ranges(
                    example,
                    spec,
                    f"{method} {path} request {content_type}",
                    errors,
                )

        if operation_id in DATA_DERIVED_OPERATIONS:
            for status, raw_response in responses.items():
                if str(status) == "304":
                    response = resolve(spec, raw_response)
                    if "X-Data-State" not in response.get("headers", {}):
                        fail(
                            errors, f"{operation_id} response 304 missing X-Data-State"
                        )
                    continue
                if not str(status).startswith("2"):
                    continue
                response = resolve(spec, raw_response)
                media = response_json_media(spec, raw_response)
                if media is None:
                    if status == "204" and operation_id == "revokeMyDataConnection":
                        fail(
                            errors,
                            "revokeMyDataConnection must return a freshness-bearing body",
                        )
                    continue
                if "X-Data-State" not in response.get("headers", {}):
                    fail(
                        errors, f"{operation_id} response {status} missing X-Data-State"
                    )
                if not schema_requires_property(spec, media["schema"], "dataFreshness"):
                    fail(
                        errors,
                        f"{operation_id} response {status} schema must require dataFreshness",
                    )
                if schema_declares_property(spec, media["schema"], "result"):
                    fail(
                        errors,
                        f"{operation_id} response {status} must not declare a public result envelope",
                    )
                for source, example in iter_content_examples(media):
                    if isinstance(example, dict) and "result" in example:
                        fail(
                            errors,
                            f"{operation_id} response {status} example {source} uses a forbidden top-level result envelope",
                        )
                direct_payload_count += 1

    for operation_id, required_states in CORE_READ_STATE_COVERAGE.items():
        actual_states = operation_success_states.get(operation_id, set())
        missing_states = required_states - actual_states
        if missing_states:
            fail(
                errors,
                f"{operation_id} success examples missing data states {sorted(missing_states)}; found {sorted(actual_states)}",
            )

    response_components = spec["components"].get("responses", {})
    if "GoalChanged" in response_components:
        fail(
            errors,
            "shared GoalChanged response is forbidden; use operation-specific responses",
        )
    goal_mutation_checks = 0
    for operation_id, (
        response_ref,
        schema_ref,
        expected_state,
        expected_change_reason,
    ) in GOAL_MUTATION_CONTRACTS.items():
        operation = op_by_id.get(operation_id, (None, None, {}))[2]
        raw_response = operation.get("responses", {}).get("200")
        if raw_response != {"$ref": response_ref}:
            fail(errors, f"{operation_id} must use {response_ref}")
            continue
        response = resolve(spec, raw_response)
        media = response_json_media(spec, response)
        if media is None or media.get("schema") != {"$ref": schema_ref}:
            fail(errors, f"{operation_id} must advertise schema {schema_ref}")
            continue
        examples = list(iter_content_examples(media))
        if not examples:
            fail(errors, f"{operation_id} must have an operation-specific example")
            continue
        for source, example in examples:
            if not isinstance(example, dict) or example.get("state") != expected_state:
                fail(
                    errors,
                    f"{operation_id} example {source} must return state {expected_state}",
                )
                continue
            actual_change_reason = example.get("progress", {}).get("changeReason")
            if actual_change_reason != expected_change_reason:
                fail(
                    errors,
                    f"{operation_id} example {source} must use progress.changeReason {expected_change_reason}",
                )
            wrong_state = next(
                state
                for state in ("ACTIVE", "PAUSED", "CANCELLED")
                if state != expected_state
            )
            invalid_example = copy.deepcopy(example)
            invalid_example["state"] = wrong_state
            validator = OAS30Validator(
                media["schema"],
                resolver=RefResolver.from_schema(spec),
                format_checker=FormatChecker(),
            )
            if not list(validator.iter_errors(invalid_example)):
                fail(
                    errors,
                    f"{operation_id} schema accepts invalid resulting state {wrong_state}",
                )
            invalid_reason_example = copy.deepcopy(example)
            invalid_reason_example.setdefault("progress", {})["changeReason"] = (
                "GOAL_CONFIRMED"
                if expected_change_reason != "GOAL_CONFIRMED"
                else "NEW_FINANCIAL_DATA"
            )
            if not list(validator.iter_errors(invalid_reason_example)):
                fail(
                    errors,
                    f"{operation_id} schema accepts an invalid progress.changeReason",
                )
        goal_mutation_checks += 1

    replacement_schema = schemas.get("GoalReplacementResult", {})
    replacement_properties = replacement_schema.get("properties", {})
    expected_replacement_properties = {
        "replacedGoal": {"$ref": "#/components/schemas/ReplacedGoal"},
        "activeGoal": {"$ref": "#/components/schemas/ReplacementActiveGoal"},
    }
    for property_name, expected_schema in expected_replacement_properties.items():
        if replacement_properties.get(property_name) != expected_schema:
            fail(
                errors,
                f"GoalReplacementResult.{property_name} must use its state-specific schema",
            )
    for required_property in (
        "replacementConfirmedAt",
        "transactionCommittedAt",
        "replacedQuestOutcomes",
        "createdQuests",
    ):
        if required_property not in replacement_schema.get("required", []):
            fail(errors, f"GoalReplacementResult must require {required_property}")
    for schema_name, expected_state in (
        ("ReplacedGoal", "CANCELLED"),
        ("ReplacementActiveGoal", "ACTIVE"),
    ):
        variants = schemas.get(schema_name, {}).get("allOf", [])
        state_restrictions = [
            variant.get("properties", {}).get("state", {}).get("enum")
            for variant in variants
            if isinstance(variant, dict)
        ]
        if [expected_state] not in state_restrictions:
            fail(errors, f"{schema_name} must restrict state to {expected_state}")

    quest_schema = schemas.get("Quest", {})
    for property_name in ("difficulty", "evidence"):
        if property_name not in quest_schema.get("required", []):
            fail(errors, f"Quest must require {property_name}")
    evidence_schema = schemas.get("QuestEvidence", {})
    required_evidence_fields = {
        "evidenceId",
        "evidenceType",
        "verificationSource",
        "verificationStatus",
        "sourceReferenceHash",
        "occurredAt",
        "verifiedAt",
    }
    if not required_evidence_fields.issubset(evidence_schema.get("required", [])):
        fail(
            errors,
            "QuestEvidence must persist the canonical verification source and lineage",
        )
    if evidence_schema.get("properties", {}).get("verificationSource") != {
        "$ref": "#/components/schemas/VerificationSource"
    }:
        fail(
            errors, "QuestEvidence.verificationSource must reference VerificationSource"
        )

    complete_operation = op_by_id.get("completeQuest", (None, None, {}))[2]
    complete_media = response_json_media(
        spec, complete_operation.get("responses", {}).get("200", {})
    )
    completion_states: set[str] = set()
    saw_completed_challenge = False
    if complete_media is not None:
        for source, example in iter_content_examples(complete_media):
            if not isinstance(example, dict):
                continue
            status = example.get("status")
            if isinstance(status, str):
                completion_states.add(status)
            if status == "COMPLETED" and example.get("difficulty") == "CHALLENGE":
                evidence = example.get("evidence", [])
                evidence_sources = {
                    item.get("verificationSource")
                    for item in evidence
                    if isinstance(item, dict)
                }
                if (
                    not evidence
                    or any(
                        item.get("verificationStatus") != "VERIFIED"
                        or not item.get("verificationSource")
                        for item in evidence
                        if isinstance(item, dict)
                    )
                    or evidence_sources != CHALLENGE_VERIFICATION_SOURCES
                ):
                    fail(
                        errors,
                        f"completeQuest example {source} must expose all CHALLENGE verified evidence sources",
                    )
                elif (
                    example.get("progress", {}).get("currentCount") != 3
                    or example.get("progress", {}).get("targetCount") != 3
                    or example.get("reward", {}).get("questXp") != 0
                ):
                    fail(
                        errors,
                        f"completeQuest example {source} must complete the 3-source zero-XP CHALLENGE",
                    )
                else:
                    saw_completed_challenge = True
    if not {"DATA_PENDING", "COMPLETED"}.issubset(completion_states):
        fail(errors, "completeQuest must example both DATA_PENDING and COMPLETED")
    if not saw_completed_challenge:
        fail(errors, "completeQuest must include a completed CHALLENGE example")

    for name, response in response_components.items():
        if name in {"GoalPaused", "GoalResumed", "GoalCancelled", "QuestChanged"}:
            continue
        if "X-Request-Id" not in resolve(spec, response).get("headers", {}):
            fail(errors, f"reusable error response {name} missing X-Request-Id")

    card_union = schemas.get("RecommendationCard", {})
    if (
        len(card_union.get("oneOf", [])) != 2
        or card_union.get("discriminator", {}).get("propertyName") != "cardKind"
    ):
        fail(
            errors,
            "RecommendationCard must be a two-variant cardKind discriminated union",
        )
    group_card = schemas.get("GroupRoutineCard", {})
    group_props = group_card.get("properties", {})
    if group_props.get("cohortSize", {}).get("minimum") != 30:
        fail(errors, "GroupRoutineCard.cohortSize must have minimum 30")
    if {"anonymousName", "avatarKey"} & set(group_props):
        fail(errors, "GroupRoutineCard must not expose personal name or avatar fields")

    for union_name in ("MetricValue", "TargetDefinition"):
        union = schemas.get(union_name, {})
        if not union.get("oneOf") or not union.get("discriminator"):
            fail(errors, f"{union_name} must be a discriminated union")
    basis_points = schemas.get("BasisPointValue", {})
    if (
        basis_points.get("type") != "integer"
        or basis_points.get("minimum") != 0
        or basis_points.get("maximum") != 10000
    ):
        fail(errors, "BasisPointValue must be integer 0..10000")

    progress = schemas.get("ProgressSnapshot", {})
    if "calculationVersion" not in progress.get("required", []):
        fail(errors, "ProgressSnapshot must require calculationVersion")
    if "PAUSED" not in schemas.get("QuestStatus", {}).get("enum", []):
        fail(errors, "QuestStatus must include PAUSED")
    expected_goal_states = {
        "CONFIRMED",
        "ACTIVE",
        "PAUSED",
        "COMPLETED",
        "EXPIRED",
        "CANCELLED",
    }
    if set(schemas.get("GoalStatus", {}).get("enum", [])) != expected_goal_states:
        fail(errors, "GoalStatus must match the persisted UserGoal state machine")

    created_connection = schemas.get("CreatedMyDataConnectionResponse", {})
    if "initialSyncJob" not in created_connection.get("required", []):
        fail(errors, "connection creation response must require initialSyncJob")
    for schema_name in ("CreateMyDataConnectionRequest", "MyDataConnection"):
        provider_values = (
            schemas.get(schema_name, {})
            .get("properties", {})
            .get("provider", {})
            .get("enum")
        )
        if provider_values != ["SYNTHETIC"]:
            fail(errors, f"{schema_name}.provider must be synthetic-only for MVP")

    return {
        "operations": len(op_by_id),
        "schemas": len(schemas),
        "successExamples": response_example_count,
        "stateCoverage": sum(
            len(states) for states in CORE_READ_STATE_COVERAGE.values()
        ),
        "goalMutations": goal_mutation_checks,
        "questCompletionStates": len(completion_states),
        "metricRangeExamples": metric_range_examples,
        "privacyMutations": privacy_mutation_count,
        "questCancellations": quest_cancel_count,
        "directPayloads": direct_payload_count,
        **sync_stats,
        **recommendation_stats,
        **metric_stats,
    }


def check_temporal_order(value: Any, label: str, errors: list[str]) -> int:
    checked = 0
    for candidate in iter_objects(value):
        if "requestedAt" not in candidate:
            continue
        checked += 1
        requested_raw = candidate.get("requestedAt")
        started_raw = candidate.get("startedAt")
        completed_raw = candidate.get("completedAt")
        try:
            requested = parse_instant(requested_raw)
            started = parse_instant(started_raw) if started_raw is not None else None
            completed = (
                parse_instant(completed_raw) if completed_raw is not None else None
            )
        except (TypeError, ValueError) as exc:
            fail(errors, f"{label} sync timestamp: {exc}")
            continue
        if started is not None and requested > started:
            fail(
                errors,
                f"{label} sync timestamp order invalid: requestedAt {requested_raw} > startedAt {started_raw}",
            )
        if completed is not None and started is None:
            fail(errors, f"{label} completedAt requires startedAt")
        elif completed is not None and started is not None and started > completed:
            fail(
                errors,
                f"{label} sync timestamp order invalid: startedAt {started_raw} > completedAt {completed_raw}",
            )
    return checked


def check_goal_replacement_example(
    replacement: dict[str, Any], errors: list[str]
) -> int:
    old_goal = replacement.get("replacedGoal", {})
    new_goal = replacement.get("activeGoal", {})
    old_goal_id = old_goal.get("goalId")
    new_goal_id = new_goal.get("goalId")
    if (
        old_goal.get("state") != "CANCELLED"
        or old_goal.get("cancellationReason") != "RECALIBRATED"
        or old_goal.get("replacedByGoalId") != new_goal_id
        or old_goal.get("recalibration", {}).get("required") is not False
        or new_goal.get("state") != "ACTIVE"
        or new_goal.get("replacesGoalId") != old_goal_id
        or not old_goal_id
        or old_goal_id == new_goal_id
    ):
        fail(
            errors,
            "goal replacement must atomically cancel and link the old goal to a distinct active replacement",
        )

    old_quest_ids = set(old_goal.get("linkedQuestIds", []))
    new_quest_ids = set(new_goal.get("linkedQuestIds", []))
    created_quests = replacement.get("createdQuests", [])
    created_quest_ids = {
        item.get("questId") for item in created_quests if isinstance(item, dict)
    }
    outcomes = replacement.get("replacedQuestOutcomes", [])
    outcome_ids = {item.get("questId") for item in outcomes if isinstance(item, dict)}
    if not old_quest_ids or not new_quest_ids or old_quest_ids & new_quest_ids:
        fail(
            errors,
            "goal replacement old and new quest IDs must be non-empty and disjoint",
        )
    if created_quest_ids != new_quest_ids:
        fail(
            errors,
            "createdQuests questIds must exactly match the active goal linkedQuestIds",
        )
    if len(created_quests) != len(created_quest_ids):
        fail(errors, "createdQuests must not repeat a questId")
    for created_quest in created_quests:
        if not isinstance(created_quest, dict):
            fail(errors, "createdQuests entries must be objects")
            continue
        if created_quest.get("initialStatus") not in {"AVAILABLE", "IN_PROGRESS"}:
            fail(errors, "created quest must expose a valid initialStatus")
    if outcome_ids != old_quest_ids:
        fail(
            errors,
            "replacedQuestOutcomes must cover every old linked quest exactly once",
        )
    if len(outcomes) != len(outcome_ids):
        fail(errors, "replacedQuestOutcomes must not repeat a questId")
    for outcome in outcomes:
        if not isinstance(outcome, dict):
            continue
        if outcome.get("terminalStatus") not in {"COMPLETED", "EXPIRED", "CANCELLED"}:
            fail(errors, "replaced quest outcome must expose a terminal status")
        if not outcome.get("terminalReason"):
            fail(errors, "replaced quest outcome must expose a terminal reason")

    confirmed_raw = replacement.get("replacementConfirmedAt")
    committed_raw = replacement.get("transactionCommittedAt")
    try:
        confirmed = parse_instant(confirmed_raw)
        committed = parse_instant(committed_raw)
    except (TypeError, ValueError) as exc:
        fail(errors, f"goal replacement timestamp: {exc}")
        return 0
    if committed < confirmed:
        fail(errors, "goal replacement transactionCommittedAt precedes confirmation")

    generated_timestamps = {
        "transactionCommittedAt": committed_raw,
        "replacedGoal.progress.calculatedAt": old_goal.get("progress", {}).get(
            "calculatedAt"
        ),
        "replacedGoal.dataFreshness.calculatedAt": old_goal.get(
            "dataFreshness", {}
        ).get("calculatedAt"),
        "activeGoal.confirmedAt": new_goal.get("confirmedAt"),
        "activeGoal.progress.calculatedAt": new_goal.get("progress", {}).get(
            "calculatedAt"
        ),
        "activeGoal.dataFreshness.calculatedAt": new_goal.get("dataFreshness", {}).get(
            "calculatedAt"
        ),
        "dataFreshness.calculatedAt": replacement.get("dataFreshness", {}).get(
            "calculatedAt"
        ),
    }
    for index, outcome in enumerate(outcomes):
        if isinstance(outcome, dict):
            generated_timestamps[f"replacedQuestOutcomes[{index}].terminatedAt"] = (
                outcome.get("terminatedAt")
            )
    for index, created_quest in enumerate(created_quests):
        if isinstance(created_quest, dict):
            generated_timestamps[f"createdQuests[{index}].createdAt"] = (
                created_quest.get("createdAt")
            )
    for path, raw_value in generated_timestamps.items():
        try:
            timestamp = parse_instant(raw_value)
        except (TypeError, ValueError) as exc:
            fail(errors, f"goal replacement {path}: {exc}")
            continue
        if timestamp < confirmed:
            fail(
                errors,
                f"goal replacement {path} {raw_value} precedes replacementConfirmedAt {confirmed_raw}",
            )
    try:
        starts_on = datetime.fromisoformat(new_goal.get("startsOn", "")).date()
        if starts_on < confirmed.date():
            fail(errors, "active replacement goal startsOn precedes confirmation date")
    except (TypeError, ValueError):
        fail(errors, "active replacement goal startsOn must be an ISO date")
    return len(generated_timestamps) + 1


def privacy_fixture_violations(fixtures: dict[str, Any]) -> list[str]:
    withdrawal = fixtures["withdrawal"]
    stale_opt_in = fixtures["staleOptIn"]
    success = fixtures["success"]
    stale_conflict = fixtures["staleConflict"]
    violations: list[str] = []

    aggregate_ids = {
        value.get("consentAggregateId")
        for value in (withdrawal, stale_opt_in, success)
        if isinstance(value, dict)
    }
    metadata = stale_conflict.get("metadata", {})
    aggregate_ids.add(metadata.get("consentAggregateId"))
    if len(aggregate_ids) != 1 or None in aggregate_ids:
        violations.append("privacy fixtures must use one consentAggregateId")

    withdrawal_version = withdrawal.get("expectedVersion")
    stale_version = stale_opt_in.get("expectedVersion")
    current_version = success.get("version")
    if any(
        isinstance(value, bool) or not isinstance(value, int)
        for value in (withdrawal_version, stale_version, current_version)
    ):
        violations.append("privacy fixture versions must be integers")
    elif (
        withdrawal_version != stale_version or current_version != withdrawal_version + 1
    ):
        violations.append(
            "withdrawal and delayed opt-in must race from one version before withdrawal wins"
        )

    if withdrawal.get("anonymousCardOptIn") is not False:
        violations.append("successful privacy request must withdraw publication")
    if stale_opt_in.get("anonymousCardOptIn") is not True:
        violations.append("stale privacy request must be the delayed opt-in")
    if (
        success.get("anonymousCardOptIn") is not False
        or success.get("shareConsentState") != "WITHDRAWN"
    ):
        violations.append("successful privacy response must preserve the withdrawal")
    if stale_conflict.get("status") != 412 or stale_conflict.get("code") != (
        "PRECONDITION_FAILED"
    ):
        violations.append("stale delayed opt-in must return 412 PRECONDITION_FAILED")
    if stale_conflict.get("retryable") is not False:
        violations.append("stale delayed opt-in must not be automatically retried")
    expected_metadata = {
        "consentAggregateId": success.get("consentAggregateId"),
        "expectedVersion": stale_version,
        "currentVersion": current_version,
        "currentState": "WITHDRAWN",
        "mutationApplied": False,
    }
    if metadata != expected_metadata:
        violations.append(
            "stale conflict metadata must prove the later withdrawal was not overwritten"
        )
    return violations


def check_privacy_fixtures(errors: list[str]) -> int:
    fixture_names = {
        "withdrawal": "privacy-settings-request.json",
        "staleOptIn": "privacy-settings-stale-opt-in-request.json",
        "success": "privacy-settings-response.json",
        "staleConflict": "privacy-settings-stale-conflict.json",
    }
    fixtures: dict[str, Any] = {}
    for key, file_name in fixture_names.items():
        path = EXAMPLE_DIR / file_name
        if not path.exists():
            fail(errors, f"required privacy fixture missing: {path}")
            continue
        try:
            fixtures[key] = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            fail(errors, f"privacy fixture {path} is invalid JSON: {exc}")
    if set(fixtures) != set(fixture_names):
        return 0

    violations = privacy_fixture_violations(fixtures)
    for violation in violations:
        fail(errors, f"privacy concurrency fixtures: {violation}")
    if violations:
        return 0

    mutations = (
        (
            "stale request accepted",
            lambda value: value["staleConflict"].__setitem__("status", 200),
        ),
        (
            "stale opt-in applied",
            lambda value: value["staleConflict"]["metadata"].__setitem__(
                "mutationApplied", True
            ),
        ),
        (
            "withdrawal overwritten",
            lambda value: value["success"].__setitem__("anonymousCardOptIn", True),
        ),
        (
            "stale request version advanced",
            lambda value: value["staleOptIn"].__setitem__(
                "expectedVersion", value["success"]["version"]
            ),
        ),
    )
    for label, mutate in mutations:
        invalid = copy.deepcopy(fixtures)
        mutate(invalid)
        if not privacy_fixture_violations(invalid):
            fail(errors, f"privacy fixture mutation survived: {label}")
    return len(mutations)


def check_examples(spec: dict[str, Any], errors: list[str]) -> dict[str, int]:
    references = {
        str((API_DIR / value).resolve())
        for value in walk_external_values(spec)
        if value.endswith(".json")
    }
    files = {str(path.resolve()) for path in EXAMPLE_DIR.glob("*.json")}
    for missing in sorted(references - files):
        fail(errors, f"referenced example missing: {missing}")
    for orphan in sorted(files - references):
        fail(errors, f"example is not linked from OpenAPI: {orphan}")

    privacy_fixture_mutations = check_privacy_fixtures(errors)
    saw_linked_paused_quest = False
    saw_unlinked_running_quest = False
    replacement_example: dict[str, Any] | None = None
    partial_sync_example: dict[str, Any] | None = None
    temporal_order_count = 0
    for file_name in sorted(files):
        data = json.loads(Path(file_name).read_text(encoding="utf-8"))
        temporal_order_count += check_temporal_order(data, file_name, errors)
        if Path(file_name).name == "goal-replacement-response.json":
            replacement_example = data
        if Path(file_name).name == "sync-partial-response.json":
            partial_sync_example = data
        stack = [data]
        while stack:
            value = stack.pop()
            if isinstance(value, dict):
                if value.get("cardKind") == "GROUP_ROUTINE":
                    if value.get("cohortSize", 0) < 30:
                        fail(errors, f"{file_name} aggregates fewer than 30 users")
                    if {"anonymousName", "avatarKey"} & set(value):
                        fail(
                            errors,
                            f"{file_name} group card exposes personal presentation fields",
                        )
                if {"questId", "goalId", "status", "pausedFromStatus"}.issubset(value):
                    if value["status"] == "PAUSED":
                        if value["goalId"] is None or value["pausedFromStatus"] not in {
                            "AVAILABLE",
                            "IN_PROGRESS",
                            "DATA_PENDING",
                        }:
                            fail(
                                errors,
                                f"{file_name} paused quest must be linked and retain its prior state",
                            )
                        saw_linked_paused_quest = True
                    elif value["goalId"] is None:
                        if value["pausedFromStatus"] is not None:
                            fail(
                                errors,
                                f"{file_name} unlinked running quest cannot carry a paused state",
                            )
                        saw_unlinked_running_quest = True
                stack.extend(value.values())
            elif isinstance(value, list):
                stack.extend(value)
    if not saw_linked_paused_quest:
        fail(errors, "examples must exercise a paused goal-linked quest")
    if not saw_unlinked_running_quest:
        fail(errors, "examples must show an unlinked quest unaffected by goal pause")
    if replacement_example is None:
        fail(errors, "atomic goal replacement example is missing")
        replacement_timestamp_count = 0
    else:
        replacement_timestamp_count = check_goal_replacement_example(
            replacement_example, errors
        )
    if partial_sync_example is None:
        fail(errors, "partial sync example is missing")
    else:
        freshness = partial_sync_example.get("dataFreshness", {})
        if (
            partial_sync_example.get("status") != "PARTIAL_FAILED"
            or freshness.get("state") != "STALE"
            or not freshness.get("lastSyncedAt")
            or not freshness.get("lastPartialSyncedAt")
            or freshness.get("lastSyncedAt") == freshness.get("lastPartialSyncedAt")
        ):
            fail(
                errors,
                "partial sync example must preserve full lastSyncedAt and advance a distinct lastPartialSyncedAt",
            )
    return {
        "examples": len(files),
        "temporalOrders": temporal_order_count,
        "replacementTimestamps": replacement_timestamp_count,
        "privacyFixtureMutations": privacy_fixture_mutations,
    }


def check_domain_and_data(errors: list[str]) -> dict[str, int]:
    files = sorted((VNEXT_DIR / "03-domain").glob("*")) + sorted(
        (VNEXT_DIR / "04-data").glob("*")
    )
    text_by_path = {
        path: path.read_text(encoding="utf-8") for path in files if path.is_file()
    }
    combined = "\n".join(text_by_path.values())

    for path in files:
        if path.suffix in {".yaml", ".yml"}:
            yaml.safe_load(path.read_text(encoding="utf-8"))

    forbidden_patterns = {
        r"\bREVOKED\b": "consent terminal state must be WITHDRAWN",
        r"PAUSED\s*-->\s*CANDIDATE": "UserGoal must never transition from PAUSED to CANDIDATE",
        r"GOAL_COMPLETED\s*-->\s*NO_GOAL": "GOAL_COMPLETED must not transition to NO_GOAL from report viewing",
        r"3영업일": "maturity reinvestment must use ISO P3D calendar days",
        r"\bIDEMPOTENCY_KEY_REUSED\b": "idempotency conflicts must use IDEMPOTENCY_CONFLICT",
    }
    for pattern, message in forbidden_patterns.items():
        if re.search(pattern, combined):
            fail(errors, message)

    required_tokens = {
        "arbitrary-precision": "calculation policy must require arbitrary-precision intermediates",
        "checked int64": "persistence must use checked int64 conversion",
        "last_partial_synced_at": "data contract must define last_partial_synced_at",
        "canonicalization_version": "canonical rows must carry canonicalization_version",
        "provider_transaction_id": "canonical transaction must define provider_transaction_id",
        "provider_created_at": "provider timestamps must be defined",
        "sync_run_id": "canonical rows must carry sync lineage",
        "source_payload_sha256": "canonical rows must preserve the winning source payload hash",
        "canonical_record_sha256": "canonical rows must preserve the deterministic winner hash",
        "verification_source": "quest evidence must persist its canonical verification source",
        "interface ProviderAccount": "ProviderAccount must be fully defined",
        "interface ProviderTransaction": "ProviderTransaction must be fully defined",
        "interface ProviderBalance": "ProviderBalance must be fully defined",
        "WITHDRAWN": "WITHDRAWN consent state must be declared",
        "GROUP_ROUTINE": "aggregate recommendation behavior must be declared",
        "RECALIBRATED": "atomic replacement cancellation reason must be declared",
        "5 / 5 = 10000 bp": "STANDARD budget-range progress must use requiredCount=5",
        "P3D": "maturity reinvestment must use the ISO P3D calendar-day window",
    }
    for token, message in required_tokens.items():
        if token not in combined:
            fail(errors, message)

    if "SCHEDULE_AND_AMOUNT_RECONFIRMED" in combined:
        fail(
            errors,
            "SCHEDULE_AND_AMOUNT_RECONFIRMED must be replaced by a declared verifiable event",
        )

    state_machine = text_by_path[VNEXT_DIR / "03-domain" / "state-machines.md"]
    if not re.search(r"같은 키와 다른 본문[^\n]*`IDEMPOTENCY_CONFLICT`", state_machine):
        fail(errors, "state machine idempotency guard must use IDEMPOTENCY_CONFLICT")
    if not re.search(r"재개 요청[^\n]*`RECALIBRATION_REQUIRED`", state_machine):
        fail(errors, "state machine resume guard must use RECALIBRATION_REQUIRED")
    if "GOAL_COMPLETED --> WAITING_FOR_DATA: new goal ACTIVE" not in state_machine:
        fail(errors, "GOAL_COMPLETED must remain until a new goal is activated")
    if "완료 리포트 조회는 상태 전이를 일으키지 않는다" not in state_machine:
        fail(errors, "state machine must declare report viewing side-effect free")

    scenarios = text_by_path[VNEXT_DIR / "04-data" / "synthetic-scenarios.md"]
    sections = re.split(r"(?=^### S\d{2}\.)", scenarios, flags=re.MULTILINE)[1:]
    if len(sections) != 18:
        fail(errors, f"expected 18 synthetic scenarios, found {len(sections)}")
    envelope_count = 0
    for section in sections:
        scenario_id = re.match(r"### (S\d{2})", section).group(1)
        seen_s07_cases: set[str] = set()
        try:
            envelopes = tagged_json_blocks(section, "calculation-envelope")
        except json.JSONDecodeError as exc:
            fail(errors, f"{scenario_id} calculation envelope is invalid JSON: {exc}")
            continue
        expected_envelopes = EXPECTED_SCENARIO_ENVELOPES[scenario_id]
        if len(envelopes) != expected_envelopes:
            fail(
                errors,
                f"{scenario_id} must contain {expected_envelopes} tagged calculation envelope(s), found {len(envelopes)}",
            )
        if not envelopes:
            continue
        for index, envelope in enumerate(envelopes, start=1):
            envelope_count += 1
            label = f"{scenario_id} envelope {index}"
            if not isinstance(envelope, dict):
                fail(errors, f"{label} must be a JSON object")
                continue
            if tuple(envelope) != CALCULATION_ENVELOPE_FIELDS:
                fail(
                    errors,
                    f"{label} fields/order must be {list(CALCULATION_ENVELOPE_FIELDS)}, found {list(envelope)}",
                )
            if envelope.get("calculationVersion") != "goal-calc-1.0.0":
                fail(errors, f"{label} has an unsupported calculationVersion")
            if (
                not isinstance(envelope.get("sourceDataVersion"), str)
                or not envelope["sourceDataVersion"]
            ):
                fail(errors, f"{label} sourceDataVersion must be a non-empty string")
            if envelope.get("dataState") not in DATA_STATES:
                fail(errors, f"{label} has an invalid dataState")
            if not isinstance(envelope.get("result"), dict):
                fail(errors, f"{label} result must be an object")
            result = envelope.get("result", {})
            for result_object in iter_objects(result):
                for key, value in result_object.items():
                    if (
                        key in {"errorCode", "differentBodyErrorCode"}
                        and value not in (CONTRACT_CODES["errorCodes"])
                    ):
                        fail(errors, f"{label} uses unknown error code {value}")
                    if (
                        key.lower().endswith("recalibrationreason")
                        and value not in (CONTRACT_CODES["recalibrationReasons"])
                    ):
                        fail(
                            errors, f"{label} uses unknown recalibration reason {value}"
                        )
            try:
                calculated_at = parse_instant(envelope.get("calculatedAt"))
                last_synced_at = (
                    parse_instant(envelope.get("lastSyncedAt"))
                    if envelope.get("lastSyncedAt") is not None
                    else None
                )
            except (TypeError, ValueError) as exc:
                fail(errors, f"{label}: {exc}")
                continue
            if last_synced_at is not None and calculated_at < last_synced_at:
                fail(errors, f"{label} calculatedAt precedes lastSyncedAt")
            if scenario_id == "S09" and result.get("recalibrationReason") != (
                "BASELINE_CHANGED_2000_BPS_OR_MORE"
            ):
                fail(errors, "S09 must use the canonical baseline-change reason")
            if (
                scenario_id == "S10"
                and result.get("differentBodyErrorCode")
                != (CONTRACT_CODES["idempotencyConflictCode"])
            ):
                fail(errors, "S10 must use the canonical idempotency conflict code")
            if scenario_id == "S07":
                expected_cases = {
                    "STALE_AFTER_GRACE": ("STALE", "SUCCEEDED", True),
                    "VERIFICATION_PENDING": ("PENDING", "SUCCEEDED", True),
                    "FULL_SYNC_FRESH": ("FRESH", "SUCCEEDED", False),
                    "PARTIAL_SYNC_DOES_NOT_ADVANCE_PUBLIC_FRESHNESS": (
                        "STALE",
                        "PARTIAL_FAILED",
                        True,
                    ),
                }
                case_name = result.get("case")
                expected_case = expected_cases.get(case_name)
                if expected_case is None:
                    fail(errors, f"S07 envelope has unknown case {case_name}")
                else:
                    if case_name in seen_s07_cases:
                        fail(errors, f"S07 repeats required case {case_name}")
                    seen_s07_cases.add(case_name)
                    expected_state, expected_sync, expected_frozen = expected_case
                    if (
                        envelope.get("dataState") != expected_state
                        or result.get("syncStatus") != expected_sync
                        or result.get("progressFrozen") is not expected_frozen
                    ):
                        fail(
                            errors, f"S07 {case_name} does not match its state contract"
                        )
                    if case_name == "PARTIAL_SYNC_DOES_NOT_ADVANCE_PUBLIC_FRESHNESS":
                        if result.get(
                            "publicLastSyncedAtAdvanced"
                        ) is not False or not result.get("lastPartialSyncedAt"):
                            fail(
                                errors,
                                "S07 partial sync must preserve public lastSyncedAt",
                            )
                        previous_public_raw = result.get("previousPublicLastSyncedAt")
                        if envelope.get("lastSyncedAt") != previous_public_raw:
                            fail(
                                errors,
                                "S07 partial sync envelope lastSyncedAt must equal previousPublicLastSyncedAt",
                            )
                        try:
                            public_sync = parse_instant(previous_public_raw)
                            partial_sync = parse_instant(
                                result.get("lastPartialSyncedAt")
                            )
                            if partial_sync <= public_sync:
                                fail(
                                    errors,
                                    "S07 lastPartialSyncedAt must be later than the unchanged public lastSyncedAt",
                                )
                        except (TypeError, ValueError) as exc:
                            fail(errors, f"S07 partial sync timestamp: {exc}")
            if scenario_id == "S11":
                scenario_reasons = {
                    value
                    for key, value in result.items()
                    if key.lower().endswith("recalibrationreason")
                }
                if scenario_reasons != set(CONTRACT_CODES["recalibrationReasons"]):
                    fail(errors, "S11 must cover all canonical recalibration triggers")
            if scenario_id == "S18":
                evidence_sources = {
                    item.get("verificationSource")
                    for item in result.get("evidence", [])
                    if isinstance(item, dict)
                }
                if (
                    result.get("difficulty") != "CHALLENGE"
                    or evidence_sources != CHALLENGE_VERIFICATION_SOURCES
                    or result.get("verifiedCount") != 3
                    or result.get("xpAwarded") != 0
                ):
                    fail(
                        errors,
                        "S18 must persist all three CHALLENGE verification sources with zero XP",
                    )

        if scenario_id == "S07":
            required_s07_cases = {
                "STALE_AFTER_GRACE",
                "VERIFICATION_PENDING",
                "FULL_SYNC_FRESH",
                "PARTIAL_SYNC_DOES_NOT_ADVANCE_PUBLIC_FRESHNESS",
            }
            if seen_s07_cases != required_s07_cases:
                fail(
                    errors,
                    f"S07 must contain each required case exactly once; found {sorted(seen_s07_cases)}",
                )

    return {
        "yamlFiles": sum(path.suffix in {".yaml", ".yml"} for path in files),
        "scenarios": len(sections),
        "scenarioEnvelopes": envelope_count,
    }


def check_cross_contract_alignment(spec: dict[str, Any], errors: list[str]) -> int:
    schemas = spec["components"]["schemas"]
    state_text = (VNEXT_DIR / "03-domain" / "state-machines.md").read_text(
        encoding="utf-8"
    )
    erd_text = (VNEXT_DIR / "04-data" / "erd-and-data-dictionary.md").read_text(
        encoding="utf-8"
    )
    calculation_text = (VNEXT_DIR / "03-domain" / "calculation-policy.md").read_text(
        encoding="utf-8"
    )
    adapter_text = (VNEXT_DIR / "04-data" / "mydata-adapter.md").read_text(
        encoding="utf-8"
    )
    scenarios_text = (VNEXT_DIR / "04-data" / "synthetic-scenarios.md").read_text(
        encoding="utf-8"
    )
    api_conventions_text = (API_DIR / "api-conventions.md").read_text(encoding="utf-8")
    catalog = yaml.safe_load(
        (VNEXT_DIR / "03-domain" / "goal-template-catalog.yaml").read_text(
            encoding="utf-8"
        )
    )

    def codes(value: str) -> set[str]:
        return set(re.findall(r"`([A-Z][A-Z0-9_]*)`", value))

    def erd_codes(field: str) -> set[str]:
        line = next(
            (line for line in erd_text.splitlines() if f"`{field}`" in line), ""
        )
        contract = (
            line.split("| enum |", 1)[1].split(";", 1)[0]
            if "| enum |" in line
            else line
        )
        return codes(contract)

    def canonical_state_codes(line: str) -> set[str]:
        contract = re.search(r"정본 상태 집합은 `([^`]+)`", line)
        return {value.strip() for value in contract.group(1).split("|")}

    goal_section = state_text.split("### 2.2 `UserGoal` 상태 머신", 1)[1].split(
        "### 2.3", 1
    )[0]
    quest_section = state_text.split("## 4. 퀘스트 상태 머신", 1)[1].split("## 5.", 1)[
        0
    ]
    goal_line = next(
        line for line in goal_section.splitlines() if "정본 상태 집합" in line
    )
    quest_line = next(
        line for line in quest_section.splitlines() if "정본 상태 집합" in line
    )

    comparisons = {
        "UserGoal state diagram": (
            set(schemas["GoalStatus"]["enum"]),
            canonical_state_codes(goal_line),
        ),
        "UserGoal ERD": (
            set(schemas["GoalStatus"]["enum"]),
            erd_codes("user_goal.state"),
        ),
        "Quest state diagram": (
            set(schemas["QuestStatus"]["enum"]),
            canonical_state_codes(quest_line),
        ),
        "Quest ERD": (
            set(schemas["QuestStatus"]["enum"]),
            erd_codes("user_quest.state"),
        ),
        "Raid ERD": (
            set(schemas["RaidState"]["enum"]),
            erd_codes("raid_progress.raid_state"),
        ),
        "Sync run ERD without readiness sentinel": (
            set(schemas["SyncJob"]["properties"]["status"]["enum"]),
            erd_codes("sync_run.sync_status") - {"IDLE"},
        ),
        "Connection ERD": (
            set(schemas["MyDataConnection"]["properties"]["status"]["enum"]),
            erd_codes("mydata_connection.status"),
        ),
        "Share consent ERD": (
            set(schemas["PrivacySettings"]["properties"]["shareConsentState"]["enum"]),
            erd_codes("share_consent.state"),
        ),
    }
    for label, (api_values, document_values) in comparisons.items():
        if api_values != document_values:
            fail(
                errors,
                f"{label} enum mismatch: api={sorted(api_values)} docs={sorted(document_values)}",
            )

    catalog_sources = {
        source
        for template in catalog["templates"]
        for source in template["verificationSource"]
    }
    api_sources = set(schemas["VerificationSource"]["enum"])
    if api_sources != catalog_sources:
        fail(
            errors,
            f"VerificationSource mismatch: api-only={sorted(api_sources - catalog_sources)} catalog-only={sorted(catalog_sources - api_sources)}",
        )

    catalog_domains = set(catalog["enums"]["domain"])
    if set(schemas["GoalDomain"]["enum"]) != catalog_domains:
        fail(errors, "GoalDomain enum does not match the goal template catalog")
    if set(schemas["Difficulty"]["enum"]) != set(catalog["enums"]["difficulty"]):
        fail(errors, "Difficulty enum does not match the goal template catalog")
    if set(schemas["DataState"]["enum"]) != set(catalog["enums"]["dataState"]):
        fail(errors, "DataState enum does not match the goal template catalog")
    budget_template = next(
        (
            template
            for template in catalog["templates"]
            if template["templateId"] == "goal.spending.budget-range.v1"
        ),
        None,
    )
    if budget_template is None:
        fail(errors, "budget-range goal template is missing")
    elif (
        budget_template["difficultyPolicy"]["candidates"]["STANDARD"]["requiredCount"]
        != 5
    ):
        fail(errors, "STANDARD budget-range requiredCount must be 5")

    manifest_texts = {
        "calculation policy": calculation_text,
        "ERD": erd_text,
        "MyData adapter": adapter_text,
    }
    for label, text in manifest_texts.items():
        try:
            manifests = tagged_json_blocks(text, "canonical-transaction-contract")
        except json.JSONDecodeError as exc:
            fail(
                errors, f"{label} canonical transaction manifest is invalid JSON: {exc}"
            )
            continue
        if len(manifests) != 1:
            fail(errors, f"{label} must contain one canonical transaction manifest")
        elif manifests[0] != CANONICAL_TRANSACTION_CONTRACT:
            fail(errors, f"{label} canonical transaction manifest does not match")

    try:
        adapter_examples = tagged_json_blocks(
            adapter_text, "canonical-transaction-example"
        )
    except json.JSONDecodeError as exc:
        adapter_examples = []
        fail(errors, f"adapter canonical transaction example is invalid JSON: {exc}")
    if len(adapter_examples) != 1 or not isinstance(adapter_examples[0], dict):
        fail(errors, "adapter must contain one tagged canonical transaction example")
    elif tuple(adapter_examples[0]) != CANONICAL_TRANSACTION_FIELDS:
        fail(
            errors,
            f"adapter canonical transaction fields differ: {list(adapter_examples[0])}",
        )

    erd_entity = re.search(
        r"^    CANONICAL_TRANSACTION \{\n(.*?)^    \}",
        erd_text,
        flags=re.MULTILINE | re.DOTALL,
    )
    if erd_entity is None:
        fail(errors, "ERD CANONICAL_TRANSACTION entity is missing")
    else:
        erd_fields = []
        for line in erd_entity.group(1).splitlines():
            parts = line.strip().split()
            if len(parts) >= 2:
                erd_fields.append(snake_to_lower_camel(parts[1]))
        if tuple(erd_fields) != CANONICAL_TRANSACTION_FIELDS:
            fail(errors, f"ERD canonical transaction fields differ: {erd_fields}")

    code_registry_texts = {
        "state machines": state_text,
        "synthetic scenarios": scenarios_text,
        "API conventions": api_conventions_text,
    }
    for label, text in code_registry_texts.items():
        try:
            registries = tagged_json_blocks(text, "contract-codes")
        except json.JSONDecodeError as exc:
            fail(errors, f"{label} contract code registry is invalid JSON: {exc}")
            continue
        if len(registries) != 1:
            fail(errors, f"{label} must contain one contract code registry")
        elif registries[0] != CONTRACT_CODES:
            fail(errors, f"{label} contract code registry does not match")

    try:
        state_matrices = tagged_json_blocks(
            api_conventions_text, "state-coverage-matrix"
        )
    except json.JSONDecodeError as exc:
        state_matrices = []
        fail(errors, f"API state coverage matrix is invalid JSON: {exc}")
    expected_matrix = {
        operation_id: sorted(states)
        for operation_id, states in CORE_READ_STATE_COVERAGE.items()
    }
    if len(state_matrices) != 1 or state_matrices[0] != expected_matrix:
        fail(
            errors,
            "API conventions state coverage matrix must match executable coverage",
        )

    try:
        payload_shapes = tagged_json_blocks(
            api_conventions_text, "public-api-payload-shape"
        )
    except json.JSONDecodeError as exc:
        payload_shapes = []
        fail(errors, f"API public payload shape is invalid JSON: {exc}")
    if len(payload_shapes) != 1 or payload_shapes[0] != PUBLIC_API_PAYLOAD_SHAPE:
        fail(
            errors,
            "API conventions must keep domain payloads direct and result envelopes internal",
        )

    all_owned_text = "\n".join(
        [
            state_text,
            calculation_text,
            erd_text,
            adapter_text,
            scenarios_text,
            api_conventions_text,
            OPENAPI_PATH.read_text(encoding="utf-8"),
        ]
    )
    stale_code_aliases = {
        "IDEMPOTENCY_KEY_REUSED",
        "BASELINE_CHANGED_OVER_20_PERCENT",
        "PAUSED_OVER_30_DAYS",
        "TEMPLATE_UNAVAILABLE",
        "DATA_REVIEW_REQUIRED",
        "INCOME_CHANGED_2000_BPS_OR_MORE",
    }
    for alias in sorted(stale_code_aliases):
        if re.search(rf"\b{re.escape(alias)}\b", all_owned_text):
            fail(errors, f"non-canonical contract code remains: {alias}")

    return len(comparisons) + 19


def main() -> int:
    errors: list[str] = []
    spec = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    api_stats = check_openapi(spec, errors)
    example_stats = check_examples(spec, errors)
    data_stats = check_domain_and_data(errors)
    alignment_count = check_cross_contract_alignment(spec, errors)
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        print(f"VERIFY_FAILED errors={len(errors)}", file=sys.stderr)
        return 1
    print(
        "VERIFY_OK "
        f"operations={api_stats['operations']} schemas={api_stats['schemas']} "
        f"examples={example_stats['examples']} success_examples={api_stats['successExamples']} "
        f"yaml={data_stats['yamlFiles']} scenarios={data_stats['scenarios']} "
        f"scenario_envelopes={data_stats['scenarioEnvelopes']} "
        f"state_coverage={api_stats['stateCoverage']} goal_mutations={api_stats['goalMutations']} "
        f"quest_completion_states={api_stats['questCompletionStates']} "
        f"quest_cancellations={api_stats['questCancellations']} "
        f"privacy_mutations={api_stats['privacyMutations']} "
        f"privacy_fixture_mutations={example_stats['privacyFixtureMutations']} "
        f"sync_statuses={api_stats['syncStatuses']} "
        f"sync_terminal_examples={api_stats['syncTerminalExamples']} "
        f"sync_idle_mutations={api_stats['syncIdleMutations']} "
        f"candidate_recommendations={api_stats['candidateRecommendations']} "
        f"recommendation_mutations={api_stats['recommendationMutations']} "
        f"direct_payloads={api_stats['directPayloads']} "
        f"metric_range_examples={api_stats['metricRangeExamples']} "
        f"metric_range_negative={api_stats['metricRangeNegative']} "
        f"metric_range_positive={api_stats['metricRangePositive']} "
        f"temporal_orders={example_stats['temporalOrders']} "
        f"replacement_timestamps={example_stats['replacementTimestamps']} "
        f"alignments={alignment_count}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
