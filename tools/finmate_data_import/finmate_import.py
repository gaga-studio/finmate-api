#!/usr/bin/env python3
"""Safe transformations for the FinMate synthetic-data release.

The source repository remains the owner of raw L1/L2/L3 data.  This module
contains the product-side allowlist and normalization boundary used before any
row can enter the API database.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Mapping


RELEASE_VERSION = "v1.0.0"
RUNTIME_PROJECTION_VERSION = "synthetic-runtime-v1"
EXPECTED_ARCHIVE_SHA256 = "278226514562ec13ddb69959622bc6342fbe0b2c45e1447fa77422e3f9d3dd58"
BUNDLE_SOURCE_COMMIT = "63ca3d046eba9ec510e377a28a0083233aefff61"
L3_SOURCE_COMMIT = "22243bce34131737fc762675f0817ead08bc165a"
EXPECTED_L3_TREE_SHA256 = "dd30ae91f5e517f2a502ce46b2dfe3666107562e2dc52edb25fec48b893c3c33"
SOURCE_SCHEMA_VERSION = "1.0.0"
LOCK_MANIFEST_PATH = Path(__file__).with_name("release-manifest.json")
EXPORT_TOP_LEVEL_FILES = frozenset(
    {
        "personas.ndjson",
        "financial_activities.ndjson",
        "financial_products.ndjson",
        "investment_holdings.ndjson",
        "investment_trades.ndjson",
        "cosmetic_catalog.ndjson",
    }
)


@dataclass(frozen=True)
class DatasetReleaseManifest:
    release_version: str
    seed: int
    data_start: str
    data_end: str
    persona_count: int

    @classmethod
    def from_generation_manifest(cls, value: Mapping[str, Any]) -> "DatasetReleaseManifest":
        months = value.get("months")
        if months:
            first_month = str(months[0])
        else:
            first_month = str(value.get("data_range", "")).split("~", 1)[0]
        demo_date = str(value["demo_date"])
        if not first_month or len(first_month) != 7:
            raise ValueError("generation manifest must include the first YYYY-MM month")
        return cls(
            release_version=RELEASE_VERSION,
            seed=int(value["seed"]),
            data_start=f"{first_month}-01",
            data_end=demo_date,
            persona_count=int(value["persona_count"]),
        )


@dataclass(frozen=True)
class DatasetImportPolicy:
    runtime_l3_tables: frozenset[str] = frozenset(
        {
            "privacy_settings",
            "features",
            "cluster_profiles",
            "friendships",
            "friend_group_stats",
            "feed_events",
            "streaks",
            "routine_summaries",
            "active_goals",
            "goal_history",
            "budgets_daily",
            "sync_events",
            "retrospectives",
            "quiz_log",
            "point_ledger",
            "quest_log",
        }
    )
    golden_l3_tables: frozenset[str] = frozenset(
        {"balance_monthly", "budgets_monthly", "metrics_monthly", "stats", "stats_history"}
    )
    excluded_l3_tables: frozenset[str] = frozenset(
        {
            "app_sessions",
            "bookmarks",
            "boss_instances",
            "goal_candidates",
            "level_rules",
            "point_catalog",
            "raid_log",
            "reward_boxes",
            "title_catalog",
            "titles_earned",
        }
    )


@dataclass(frozen=True)
class SeedOperation:
    name: str
    sql: str
    rows: tuple[tuple[Any, ...], ...]


UNSAFE_QUEST_TEMPLATE_IDS = frozenset(
    {
        "QUEST-SUB-STOCK-ACCOUNT",
        "QUEST-INVEST-FIRST-BUY",
    }
)


L3_COLUMN_ALLOWLIST: dict[str, tuple[str, ...]] = {
    "privacy_settings": (
        "persona_id", "stats_visibility", "feed_visibility", "friend_compare_visibility", "goal_visibility"
    ),
    "features": (
        "persona_id", "month", "age", "cohort", "income_norm", "essential_ratio", "consumption_rate_c",
        "saving_rate_c", "invest_rate_c", "defense_score", "saving_score", "invest_score",
        "food_leisure_share", "transit_share", "shopping_beauty_share", "subscription_count", "cluster_id",
    ),
    "cluster_profiles": (
        "cluster_id", "size", "avg_age", "avg_defense_score", "avg_saving_score", "avg_invest_score",
        "avg_consumption_rate", "avg_saving_rate", "description",
    ),
    "friendships": ("persona_a", "persona_b", "status", "created_at"),
    "friend_group_stats": (
        "persona_id", "friend_count", "scored_friend_count", "locked", "avg_defense_score",
        "avg_saving_score", "avg_invest_score"
    ),
    "feed_events": (
        "viewer_persona_id", "subject_persona_id", "event_type", "message", "stat_delta", "event_date"
    ),
    "streaks": (
        "streak_type", "persona_id", "partner_persona_id", "current_streak", "best_streak", "unit"
    ),
    "routine_summaries": ("persona_id", "routine", "frequency", "ratio_pct", "maintained_months"),
    "active_goals": (
        "persona_id", "goal_id", "template_id", "title", "status", "domain", "goal_type", "user_given_name",
        "start_month", "start_value", "current_value", "target_value", "difficulty", "progress_pct",
        "best_progress_pct", "stage", "verification_source",
    ),
    "goal_history": (
        "persona_id", "goal_id", "template_id", "title", "domain", "goal_type", "user_given_name",
        "start_month", "start_value", "target_value", "achieved_at", "joined_at", "verification_source",
    ),
    "budgets_daily": ("persona_id", "date", "month", "cumulative_spend_krw", "daily_budget_krw"),
    "sync_events": ("persona_id", "sync_at", "sync_type", "new_tx_count"),
    "retrospectives": ("persona_id", "week_start", "week_end", "ai_draft_text", "user_note"),
    "quiz_log": ("persona_id", "quiz_id", "category", "date", "correct", "quest_instance_id"),
}

GOLDEN_L3_COLUMN_ALLOWLIST: dict[str, tuple[str, ...]] = {
    "balance_monthly": (
        "persona_id", "month", "month_net_change_krw", "cumulative_net_asset_change_krw",
    ),
    "budgets_monthly": (
        "persona_id", "month", "month_budget_krw", "month_spend_krw", "usage_rate",
    ),
    "metrics_monthly": (
        "persona_id", "month", "months_available", "data_sufficiency", "income_month_krw",
        "income_3m_avg_krw", "essential_spend_krw", "essential_3m_avg_krw",
        "disposable_income_krw", "discretionary_spend_krw", "consumption_rate",
        "saving_net_inflow_krw", "saving_rate", "invest_net_inflow_krw", "invest_rate",
        "invest_participation", "cum_saving_balance_est_krw", "emergency_months",
        "month_budget_krw", "month_spend_krw", "budget_usage_rate", "consumption_tx_count",
        "saving_tx_count", "invest_tx_count", "income_tx_count",
    ),
    "stats": (
        "persona_id", "month", "cohort", "defense_score", "saving_score", "invest_score",
        "invest_unlocked", "data_sufficiency", "xp", "level", "level_progress",
    ),
    "stats_history": (
        "persona_id", "date", "month", "defense_score", "saving_score", "invest_score",
        "emergency_months", "xp", "level", "level_progress",
    ),
}

_L3_SCORE_COLUMNS = frozenset(
    {"defense_score", "saving_score", "invest_score", "avg_defense_score", "avg_saving_score", "avg_invest_score"}
)
_L3_RATIO_COLUMNS = frozenset(
    {
        "essential_ratio", "consumption_rate_c", "saving_rate_c", "invest_rate_c", "food_leisure_share",
        "transit_share", "shopping_beauty_share", "avg_consumption_rate", "avg_saving_rate",
    }
)
_L3_PERCENT_COLUMNS = frozenset({"ratio_pct", "progress_pct", "best_progress_pct", "stat_delta"})
_GOLDEN_SCORE_COLUMNS = frozenset({"defense_score", "saving_score", "invest_score"})
_GOLDEN_RATIO_COLUMNS = frozenset(
    {"consumption_rate", "saving_rate", "invest_rate", "budget_usage_rate", "usage_rate", "level_progress"}
)
_GOLDEN_MONEY_COLUMNS = frozenset(
    {
        "month_net_change_krw", "cumulative_net_asset_change_krw", "month_budget_krw",
        "month_spend_krw", "income_month_krw", "income_3m_avg_krw", "essential_spend_krw",
        "essential_3m_avg_krw", "disposable_income_krw", "discretionary_spend_krw",
        "saving_net_inflow_krw", "invest_net_inflow_krw", "cum_saving_balance_est_krw",
    }
)


def is_allowed_quest_template(template_id: str) -> bool:
    return template_id not in UNSAFE_QUEST_TEMPLATE_IDS


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(chunk_size), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_archive(path: Path, expected_sha256: str = EXPECTED_ARCHIVE_SHA256) -> None:
    actual = sha256_file(path)
    if actual != expected_sha256.lower():
        raise ValueError(f"release checksum mismatch: expected {expected_sha256}, got {actual}")


def l3_tree_sha256(source_root: Path) -> str:
    l3_root = source_root / "data" / "l3"
    policy = DatasetImportPolicy()
    expected_names = {
        f"{table_name}.parquet"
        for table_name in policy.runtime_l3_tables | policy.golden_l3_tables | policy.excluded_l3_tables
    }
    actual_paths = sorted(l3_root.glob("*.parquet"), key=lambda path: path.name)
    actual_names = {path.name for path in actual_paths}
    if actual_names != expected_names:
        missing = sorted(expected_names - actual_names)
        extra = sorted(actual_names - expected_names)
        raise ValueError(f"L3 parquet file set mismatch: missing={missing}, extra={extra}")

    entries = (
        f"data/l3/{path.name}\t{sha256_file(path)}\n"
        for path in actual_paths
    )
    return hashlib.sha256("".join(entries).encode("utf-8")).hexdigest()


def verify_l3_source(
    source_root: Path,
    expected_sha256: str = EXPECTED_L3_TREE_SHA256,
) -> str:
    actual = l3_tree_sha256(source_root)
    if actual != expected_sha256.lower():
        raise ValueError(f"L3 tree checksum mismatch: expected {expected_sha256}, got {actual}")
    return actual


def expected_export_file_paths() -> frozenset[str]:
    policy = DatasetImportPolicy()
    return frozenset(
        EXPORT_TOP_LEVEL_FILES
        | {f"l3/{table}.ndjson" for table in policy.runtime_l3_tables}
        | {f"golden_l3/{table}.ndjson" for table in policy.golden_l3_tables}
    )


def export_file_sha256(output_dir: Path) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for relative_path in sorted(expected_export_file_paths()):
        path = output_dir / relative_path
        if not path.is_file():
            raise ValueError(f"required export payload is missing: {relative_path}")
        checksums[relative_path] = sha256_file(path)
    return checksums


def locked_export_file_sha256() -> dict[str, str]:
    manifest = json.loads(LOCK_MANIFEST_PATH.read_text(encoding="utf-8"))
    value = manifest.get("exportFileSha256")
    if not isinstance(value, dict) or set(value) != set(expected_export_file_paths()):
        raise RuntimeError("release manifest export file checksum set is incomplete")
    normalized = {str(path): str(checksum).lower() for path, checksum in value.items()}
    if any(len(checksum) != 64 or any(character not in "0123456789abcdef" for character in checksum)
           for checksum in normalized.values()):
        raise RuntimeError("release manifest contains an invalid export file checksum")
    return normalized


def ratio_to_bps(value: Any) -> int:
    return _scaled_bps(value, Decimal("10000"))


def score_to_bps(value: Any) -> int:
    return _scaled_bps(value, Decimal("100"))


def _scaled_bps(value: Any, multiplier: Decimal) -> int:
    scaled = (Decimal(str(value)) * multiplier).quantize(Decimal("1"), rounding=ROUND_HALF_UP)
    return max(0, min(10000, int(scaled)))


def _age_band(age: int) -> str:
    if age <= 24:
        return "20-24"
    if age <= 29:
        return "25-29"
    if age <= 34:
        return "30-34"
    return "35+"


def sanitize_profile(source: Mapping[str, Any]) -> dict[str, Any]:
    if source.get("is_synthetic") is not True:
        raise ValueError("only explicitly synthetic profiles may be imported")
    age = int(source["age"])
    return {
        "personaId": str(source["persona_id"]),
        "ageBand": _age_band(age),
        "cohort": str(source["cohort"]),
        "archetype": str(source["archetype"]),
        "occupationGroup": str(source["job"]),
        "monthlyIncomeKrw": int(source["monthly_income_krw"]),
        "incomeRegularity": str(source["income_regularity"]),
        "targetSavingRateBps": ratio_to_bps(source["target_saving_rate"]),
        "targetInvestmentRateBps": ratio_to_bps(source["target_investment_rate"]),
        "riskScore": int(source["risk_score"]),
        "riskAttitude": str(source["risk_attitude"]),
        "householdType": str(source["household_type"]),
        "lifestyleTags": list(source.get("lifestyle_tags", [])),
        "financialGoal": str(source["financial_goal"]),
        "moneyWorry": str(source["money_worry"]),
        "joinedAt": str(source["joined_at"]),
        "sourceDataRange": str(source["data_range"]),
        "sourceDataAsOf": str(source["demo_date"]),
        "synthetic": True,
    }


_ESSENTIAL_PAIRS = frozenset(
    {
        ("주거", "월세/관리비"),
        ("생활", "통신"),
        ("금융", "보험"),
        ("교통", "대중교통"),
    }
)


def _is_essential_expense(category: str, subcategory: str) -> bool:
    if (category, subcategory) in _ESSENTIAL_PAIRS:
        return True
    return any(token in category or token in subcategory for token in ("부채", "대출"))


def sanitize_ledger_row(source: Mapping[str, Any]) -> dict[str, Any]:
    raw_amount = Decimal(str(source["금액"]))
    amount = int(abs(raw_amount).quantize(Decimal("1"), rounding=ROUND_HALF_UP))
    bucket = str(source["cashflow_bucket"])
    activity_type = {
        "소득": "INCOME",
        "수입": "INCOME",
        "소비": "SPENDING",
        "저축": "SAVING",
        "투자": "INVESTMENT",
    }.get(bucket)
    if activity_type is None:
        raise ValueError(f"unsupported cashflow bucket: {bucket}")

    category = str(source["대분류"])
    subcategory = str(source["소분류"])
    classification = {
        "INCOME": "EARNED_INCOME",
        "SAVING": "SAVING_CONTRIBUTION",
        "INVESTMENT": "BROKERAGE_TRANSFER",
    }.get(activity_type)
    if classification is None:
        classification = (
            "ESSENTIAL_EXPENSE"
            if _is_essential_expense(category, subcategory)
            else "DISCRETIONARY_EXPENSE"
        )

    local_time = str(source.get("시간") or "00:00")
    if len(local_time) == 5:
        local_time += ":00"
    occurred = datetime.fromisoformat(f"{source['날짜']}T{local_time}")

    return {
        "sourceTransactionId": str(source["transaction_id"]),
        "personaId": str(source["persona_id"]),
        "activityType": activity_type,
        "direction": "INFLOW" if raw_amount >= 0 else "OUTFLOW",
        "classification": classification,
        "category": category,
        "subcategory": subcategory,
        "displayLabel": subcategory,
        "amountKrw": amount,
        "currency": str(source.get("화폐") or "KRW"),
        "occurredAt": f"{occurred.isoformat()}+09:00",
    }


def sanitize_manifest_products(source: Mapping[str, Any]) -> list[dict[str, Any]]:
    persona_id = str(source["persona_id"])
    as_of = str(source["generated_at"])[:10]
    products: list[dict[str, Any]] = []
    for product_type, key in (("SAVING_PRODUCT", "savings_product"), ("HOUSING_PRODUCT", "housing_product")):
        product_name = _optional_text(source.get(key))
        if product_name:
            products.append(
                {
                    "holdingId": f"{persona_id}:{product_type}",
                    "personaId": persona_id,
                    "productType": product_type,
                    "productName": product_name,
                    "holdingStatus": "HELD",
                    "asOfDate": as_of,
                    "synthetic": True,
                }
            )
    return products


def sanitize_investment_holding(persona_id: str, source: Mapping[str, Any], base_date: str) -> dict[str, Any]:
    product_type = str(source.get("prod_type") or "")
    category = {"101": "STOCK", "201": "ETF", "301": "CRYPTO"}.get(product_type, "OTHER")
    return {
        "holdingId": f"{persona_id}:{source['prod_code']}",
        "personaId": persona_id,
        "productName": str(source["prod_name"]),
        "ticker": str(source["prod_code"]),
        "category": category,
        "purchaseAmountKrw": _money(source.get("purchase_amt")),
        "evaluationAmountKrw": _money(source.get("eval_amt")),
        "quantity": _plain_number(source.get("holding_num")),
        "currency": str(source.get("currency_code") or "KRW"),
        "asOfDate": _compact_date(base_date),
        "synthetic": True,
    }


def sanitize_investment_trade(persona_id: str, source: Mapping[str, Any]) -> dict[str, Any]:
    action = {"매수": "BUY", "매도": "SELL"}.get(str(source.get("Bod")))
    if action is None:
        raise ValueError(f"unsupported investment action: {source.get('Bod')}")
    occurred = datetime.strptime(str(source["trans_dtime"]), "%Y%m%d%H%M%S")
    return {
        "tradeId": hashlib.sha256(
            f"{persona_id}|{source.get('prod_code')}|{source.get('trans_dtime')}|{action}|{source.get('trans_num')}".encode()
        ).hexdigest()[:32],
        "personaId": persona_id,
        "productName": str(source["prod_name"]),
        "ticker": str(source["prod_code"]),
        "action": action,
        "quantity": _plain_number(source.get("trans_num")),
        "grossAmountKrw": _money(source.get("trans_amt")),
        "settlementAmountKrw": _money(source.get("settle_amt")),
        "currency": str(source.get("currency_code") or "KRW"),
        "occurredAt": f"{occurred.isoformat()}+09:00",
        "synthetic": True,
    }


def sanitize_l3_row(table_name: str, source: Mapping[str, Any]) -> dict[str, Any] | None:
    if table_name == "quest_log":
        return sanitize_quest_row(source)
    if table_name == "point_ledger":
        return sanitize_point_row(source)
    allowed = L3_COLUMN_ALLOWLIST.get(table_name)
    if allowed is None:
        raise ValueError(f"L3 table is not approved for runtime import: {table_name}")

    normalized: dict[str, Any] = {}
    for column in allowed:
        value = source.get(column)
        if _is_missing(value):
            continue
        target_column = column
        if column in _L3_SCORE_COLUMNS:
            target_column = f"{column}_bps"
            value = score_to_bps(value)
        elif column in _L3_RATIO_COLUMNS:
            target_column = f"{column}_bps"
            value = ratio_to_bps(value)
        elif column in _L3_PERCENT_COLUMNS:
            target_column = f"{column}_bps"
            value = score_to_bps(value)
        elif table_name == "friend_group_stats" and column in {"friend_count", "scored_friend_count"}:
            count = Decimal(str(value))
            if not count.is_finite() or count != count.to_integral_value():
                raise ValueError(f"{column} must be an integer count")
            value = int(count)
        elif column.endswith("_krw"):
            value = _money(value)
        else:
            value = _json_safe(value)
        normalized[target_column] = value

    if table_name == "friend_group_stats":
        average_columns = ("avg_defense_score_bps", "avg_saving_score_bps", "avg_invest_score_bps")
        if normalized.get("locked") is True:
            for column in average_columns:
                normalized.pop(column, None)
        else:
            friend_count = normalized.get("friend_count")
            scored_friend_count = normalized.get("scored_friend_count")
            if (
                not isinstance(friend_count, int)
                or not isinstance(scored_friend_count, int)
                or scored_friend_count < 1
                or scored_friend_count > friend_count
            ):
                raise ValueError(
                    "unlocked friend_group_stats requires 1 <= scored_friend_count <= friend_count"
                )
            if any(column not in normalized for column in average_columns):
                raise ValueError("unlocked friend_group_stats requires all three average scores")
    return normalized


def sanitize_golden_l3_row(table_name: str, source: Mapping[str, Any]) -> dict[str, Any]:
    allowed = GOLDEN_L3_COLUMN_ALLOWLIST.get(table_name)
    if allowed is None:
        raise ValueError(f"L3 table is not approved for golden comparison: {table_name}")

    normalized: dict[str, Any] = {}
    for column in allowed:
        value = source.get(column)
        if _is_missing(value):
            continue
        target_column = _camel_case(column)
        if column in _GOLDEN_SCORE_COLUMNS:
            target_column = f"{target_column}Bps"
            value = score_to_bps(value)
        elif column in _GOLDEN_RATIO_COLUMNS:
            target_column = f"{target_column}Bps"
            value = ratio_to_bps(value)
        elif column in _GOLDEN_MONEY_COLUMNS:
            value = _signed_money(value)
        else:
            value = _json_safe(value)
        normalized[target_column] = value
    return normalized


def cosmetic_catalog() -> list[dict[str, Any]]:
    return [
        {
            "itemId": "cosmetic-outfit-mint",
            "name": "민트 탐험복",
            "category": "OUTFIT",
            "description": "캐릭터에 적용하는 고정형 의상",
            "costPoints": 5,
            "available": True,
            "displayOrder": 1,
            "randomized": False,
            "cashEquivalent": False,
        },
        {
            "itemId": "cosmetic-frame-sprout",
            "name": "새싹 프로필 테두리",
            "category": "PROFILE_FRAME",
            "description": "프로필에 적용하는 고정형 테두리",
            "costPoints": 15,
            "available": True,
            "displayOrder": 2,
            "randomized": False,
            "cashEquivalent": False,
        },
        {
            "itemId": "cosmetic-theme-daylight",
            "name": "맑은 민트 테마",
            "category": "THEME",
            "description": "앱 배경에 적용하는 고정형 테마",
            "costPoints": 25,
            "available": True,
            "displayOrder": 3,
            "randomized": False,
            "cashEquivalent": False,
        },
    ]


def sanitize_quest_row(source: Mapping[str, Any]) -> dict[str, Any] | None:
    template_id = str(source["template_id"])
    if not is_allowed_quest_template(template_id):
        return None
    return {
        "personaId": str(source["persona_id"]),
        "questInstanceId": str(source["quest_instance_id"]),
        "templateId": template_id,
        "category": str(source["category"]),
        "title": str(source["title"]),
        "assignedDate": str(source["assigned_date"]),
        "dueDate": str(source["due_date"]),
        "status": {
            "참여가능": "AVAILABLE",
            "진행중": "IN_PROGRESS",
            "완료": "COMPLETED",
            "만료": "EXPIRED",
            "데이터반영대기": "DATA_PENDING",
        }.get(str(source["status"]), str(source["status"]).upper()),
        "source": str(source["source"]),
        "evidenceSource": str(source["evidence_source"]),
        "currentValue": _plain_number(source.get("current_value")),
        "targetValue": _plain_number(source.get("target_value")),
        "xpReward": int(source.get("xp") or 0),
        "pointReward": int(source.get("point") or 0),
        "completedAt": _optional_text(source.get("completed_at")),
        "linkedGoalId": _optional_text(source.get("linked_goal_id")),
    }


def sanitize_point_row(source: Mapping[str, Any]) -> dict[str, Any] | None:
    reason = str(source["reason"])
    if reason.startswith("quest_complete:") and not is_allowed_quest_template(reason.split(":", 1)[1]):
        return None
    event_type = str(source["event_type"]).upper()
    delta = int(source["delta"])
    if event_type != "EARN" or delta < 0:
        return None
    return {
        "personaId": str(source["persona_id"]),
        "occurredOn": str(source["date"]),
        "delta": delta,
        "reason": reason,
        "eventType": event_type,
    }


def _plain_number(value: Any) -> int | float | None:
    if value is None or str(value).lower() == "nan":
        return None
    decimal = Decimal(str(value))
    integral = decimal.to_integral_value()
    return int(integral) if decimal == integral else float(decimal)


def _money(value: Any) -> int:
    if _is_missing(value):
        return 0
    return int(abs(Decimal(str(value))).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _signed_money(value: Any) -> int:
    if _is_missing(value):
        return 0
    return int(Decimal(str(value)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _camel_case(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


def _compact_date(value: str) -> str:
    text = str(value)
    if len(text) == 8 and text.isdigit():
        return f"{text[:4]}-{text[4:6]}-{text[6:]}"
    return text


def _is_missing(value: Any) -> bool:
    if value is None:
        return True
    if isinstance(value, float) and math.isnan(value):
        return True
    return str(value).lower() in {"nan", "nat", "none"}


def _json_safe(value: Any) -> Any:
    if isinstance(value, (datetime,)):
        return value.isoformat()
    if hasattr(value, "isoformat") and callable(value.isoformat):
        return value.isoformat()
    if isinstance(value, Decimal):
        return _plain_number(value)
    if hasattr(value, "item") and callable(value.item):
        return value.item()
    return value


def _optional_text(value: Any) -> str | None:
    if value is None or str(value).lower() in {"", "nan", "nat", "none"}:
        return None
    return str(value)


def export_release(
    source_root: Path,
    output_dir: Path,
    archive_path: Path | None = None,
    *,
    l3_source_root: Path,
    expected_l3_tree_sha256: str = EXPECTED_L3_TREE_SHA256,
    l3_row_reader: Any | None = None,
    verify_locked_export: bool = True,
) -> DatasetReleaseManifest:
    archive_verified = archive_path is not None
    if archive_path is not None:
        verify_archive(archive_path)
    verified_l3_tree_sha256 = verify_l3_source(
        l3_source_root,
        expected_sha256=expected_l3_tree_sha256,
    )
    generation_manifest_path = source_root / "validation" / "generation_manifest.json"
    generation_manifest = json.loads(generation_manifest_path.read_text(encoding="utf-8"))
    release = DatasetReleaseManifest.from_generation_manifest(generation_manifest)

    bundles_root = source_root / "data" / "bundles"
    if not bundles_root.exists():
        bundles_root = source_root / "data" / "samples" / "bundles"
    bundle_dirs = sorted(path for path in bundles_root.glob("P*") if path.is_dir())

    personas: list[dict[str, Any]] = []
    activities: list[dict[str, Any]] = []
    financial_products: list[dict[str, Any]] = []
    investment_holdings: list[dict[str, Any]] = []
    investment_trades: list[dict[str, Any]] = []
    for bundle_dir in bundle_dirs:
        profile_path = bundle_dir / "profile.json"
        ledger_path = bundle_dir / "ledger.csv"
        manifest_path = bundle_dir / "manifest.json"
        if not profile_path.exists() or not ledger_path.exists() or not manifest_path.exists():
            continue
        profile_source = json.loads(profile_path.read_text(encoding="utf-8"))
        persona_id = str(profile_source["persona_id"])
        personas.append(sanitize_profile(profile_source))
        financial_products.extend(
            sanitize_manifest_products(json.loads(manifest_path.read_text(encoding="utf-8")))
        )
        with ledger_path.open("r", encoding="utf-8-sig", newline="") as source:
            activities.extend(sanitize_ledger_row(row) for row in csv.DictReader(source))
        investment_dir = bundle_dir / "api" / "invest"
        for holding_path in sorted(investment_dir.glob("금투-004-*.json")):
            holding_source = json.loads(holding_path.read_text(encoding="utf-8"))
            base_date = str(holding_source["base_date"])
            investment_holdings.extend(
                sanitize_investment_holding(persona_id, row, base_date)
                for row in holding_source.get("prod_list", [])
            )
        for trade_path in sorted(investment_dir.glob("금투-003-*.json")):
            trade_source = json.loads(trade_path.read_text(encoding="utf-8"))
            investment_trades.extend(
                sanitize_investment_trade(persona_id, row)
                for row in trade_source.get("trans_list", [])
            )

    if len(personas) != release.persona_count:
        raise ValueError(
            f"persona count mismatch: manifest={release.persona_count}, exported={len(personas)}"
        )

    personas.sort(key=lambda item: item["personaId"])
    activities.sort(key=lambda item: (item["personaId"], item["occurredAt"], item["sourceTransactionId"]))
    financial_products.sort(key=lambda item: item["holdingId"])
    investment_holdings.sort(key=lambda item: item["holdingId"])
    investment_trades.sort(key=lambda item: item["tradeId"])
    output_dir.mkdir(parents=True, exist_ok=True)
    _write_ndjson(output_dir / "personas.ndjson", personas)
    _write_ndjson(output_dir / "financial_activities.ndjson", activities)
    _write_ndjson(output_dir / "financial_products.ndjson", financial_products)
    _write_ndjson(output_dir / "investment_holdings.ndjson", investment_holdings)
    _write_ndjson(output_dir / "investment_trades.ndjson", investment_trades)
    cosmetics = cosmetic_catalog()
    _write_ndjson(output_dir / "cosmetic_catalog.ndjson", cosmetics)
    l3_counts = export_runtime_l3(l3_source_root, output_dir, row_reader=l3_row_reader)
    golden_l3_counts = export_golden_l3(l3_source_root, output_dir, row_reader=l3_row_reader)
    export_checksums = export_file_sha256(output_dir)
    if verify_locked_export and export_checksums != locked_export_file_sha256():
        raise ValueError("export payload checksums do not match the locked release")
    import_manifest = {
        "releaseVersion": release.release_version,
        "archiveSha256": EXPECTED_ARCHIVE_SHA256,
        "archiveVerified": archive_verified,
        "schemaVersion": SOURCE_SCHEMA_VERSION,
        "bundleSourceCommit": BUNDLE_SOURCE_COMMIT,
        "l3SourceCommit": L3_SOURCE_COMMIT,
        "l3TreeSha256": verified_l3_tree_sha256,
        "seed": release.seed,
        "dataStart": release.data_start,
        "dataEnd": release.data_end,
        "counts": {
            "cosmeticCatalogItems": len(cosmetics),
            "financialActivities": len(activities),
            "financialProducts": len(financial_products),
            "investmentHoldings": len(investment_holdings),
            "investmentTrades": len(investment_trades),
            "personas": len(personas),
        },
        "exportFileSha256": export_checksums,
        "goldenL3Counts": golden_l3_counts,
        "runtimeL3Counts": l3_counts,
    }
    (output_dir / "import-manifest.json").write_text(
        json.dumps(import_manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return release


def export_runtime_l3(
    source_root: Path,
    output_dir: Path,
    *,
    table_names: frozenset[str] | None = None,
    row_reader: Any | None = None,
) -> dict[str, int]:
    selected = table_names or DatasetImportPolicy().runtime_l3_tables
    l3_root = source_root / "data" / "l3"
    missing = sorted(table for table in selected if not (l3_root / f"{table}.parquet").exists())
    if missing:
        raise ValueError(f"release is missing approved L3 tables: {', '.join(missing)}")
    reader = row_reader or _read_parquet_rows
    counts: dict[str, int] = {}
    for table_name in sorted(selected):
        normalized = []
        for source in reader(l3_root / f"{table_name}.parquet"):
            row = sanitize_l3_row(table_name, source)
            if row is not None:
                normalized.append(row)
        normalized.sort(key=_canonical_row)
        _write_ndjson(output_dir / "l3" / f"{table_name}.ndjson", normalized)
        counts[table_name] = len(normalized)
    return counts


def export_golden_l3(
    source_root: Path,
    output_dir: Path,
    *,
    table_names: frozenset[str] | None = None,
    row_reader: Any | None = None,
) -> dict[str, int]:
    selected = table_names or DatasetImportPolicy().golden_l3_tables
    l3_root = source_root / "data" / "l3"
    missing = sorted(table for table in selected if not (l3_root / f"{table}.parquet").exists())
    if missing:
        raise ValueError(f"release is missing golden L3 tables: {', '.join(missing)}")
    reader = row_reader or _read_parquet_rows
    counts: dict[str, int] = {}
    for table_name in sorted(selected):
        normalized = [sanitize_golden_l3_row(table_name, source) for source in reader(l3_root / f"{table_name}.parquet")]
        normalized.sort(key=_canonical_row)
        _write_ndjson(output_dir / "golden_l3" / f"{table_name}.ndjson", normalized)
        counts[table_name] = len(normalized)
    return counts


def _read_parquet_rows(path: Path) -> list[dict[str, Any]]:
    try:
        import pyarrow.parquet as parquet  # type: ignore
    except ImportError as exception:
        raise RuntimeError(
            "pyarrow is required for L3 import; install tools/finmate_data_import/requirements.txt"
        ) from exception
    return parquet.read_table(path).to_pylist()


def _canonical_row(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def build_seed_operations(input_dir: Path, *, allow_unverified: bool = False) -> list[SeedOperation]:
    manifest = json.loads((input_dir / "import-manifest.json").read_text(encoding="utf-8"))
    if "sourceCommit" in manifest:
        raise ValueError("legacy sourceCommit is not accepted; split bundle and L3 provenance is required")
    if manifest.get("releaseVersion") != RELEASE_VERSION:
        raise ValueError(f"unsupported release version: {manifest.get('releaseVersion')}")
    if manifest.get("archiveSha256") != EXPECTED_ARCHIVE_SHA256:
        raise ValueError("import manifest archive checksum does not match the locked release")
    if manifest.get("archiveVerified") is not True and not allow_unverified:
        raise ValueError("import manifest was exported without verifying the release archive")
    if manifest.get("bundleSourceCommit") != BUNDLE_SOURCE_COMMIT:
        raise ValueError("import manifest bundle source commit does not match the locked release")
    if manifest.get("l3SourceCommit") != L3_SOURCE_COMMIT:
        raise ValueError("import manifest L3 source commit does not match the locked L3 revision")
    if manifest.get("l3TreeSha256") != EXPECTED_L3_TREE_SHA256:
        raise ValueError("import manifest L3 tree checksum does not match the locked L3 revision")
    if manifest.get("dataEnd") != "2026-07-13":
        raise ValueError("v1.0.0 source data must end on 2026-07-13")
    _validate_export_file_checksums(input_dir, manifest, require_locked=not allow_unverified)
    _validate_l3_export(input_dir, manifest)

    release = manifest["releaseVersion"]
    operations = [
        SeedOperation(
            "dataset_release",
            """
            INSERT INTO finmate_dataset_release
                (release_version, archive_sha256, schema_version, bundle_source_commit, l3_source_commit,
                 l3_tree_sha256, period_start, period_end, imported_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
            ON CONFLICT (release_version) DO UPDATE SET
                archive_sha256 = EXCLUDED.archive_sha256,
                schema_version = EXCLUDED.schema_version,
                bundle_source_commit = EXCLUDED.bundle_source_commit,
                l3_source_commit = EXCLUDED.l3_source_commit,
                l3_tree_sha256 = EXCLUDED.l3_tree_sha256,
                period_start = EXCLUDED.period_start,
                period_end = EXCLUDED.period_end,
                imported_at = CURRENT_TIMESTAMP
            """,
            ((release, manifest["archiveSha256"], manifest["schemaVersion"], manifest["bundleSourceCommit"],
              manifest["l3SourceCommit"], manifest["l3TreeSha256"], manifest["dataStart"],
              manifest["dataEnd"]),),
        )
    ]

    personas = _read_ndjson(input_dir / "personas.ndjson")
    operations.append(
        SeedOperation(
            "personas",
            """
            INSERT INTO finmate_import_persona
                (source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
                 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
                 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
                 joined_at, source_data_range, source_data_as_of, synthetic)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (source_persona_id) DO UPDATE SET
                release_version = EXCLUDED.release_version,
                age_band = EXCLUDED.age_band,
                cohort = EXCLUDED.cohort,
                archetype = EXCLUDED.archetype,
                occupation_group = EXCLUDED.occupation_group,
                monthly_income_krw = EXCLUDED.monthly_income_krw,
                income_regularity = EXCLUDED.income_regularity,
                target_saving_rate_bps = EXCLUDED.target_saving_rate_bps,
                target_investment_rate_bps = EXCLUDED.target_investment_rate_bps,
                risk_score = EXCLUDED.risk_score,
                risk_attitude = EXCLUDED.risk_attitude,
                household_type = EXCLUDED.household_type,
                lifestyle_tags = EXCLUDED.lifestyle_tags,
                financial_goal = EXCLUDED.financial_goal,
                money_worry = EXCLUDED.money_worry,
                joined_at = EXCLUDED.joined_at,
                source_data_range = EXCLUDED.source_data_range,
                source_data_as_of = EXCLUDED.source_data_as_of,
                synthetic = EXCLUDED.synthetic
            """,
            tuple(
                (
                    row["personaId"], release, row["ageBand"], row["cohort"], row["archetype"],
                    row["occupationGroup"], row["monthlyIncomeKrw"], row["incomeRegularity"],
                    row["targetSavingRateBps"], row["targetInvestmentRateBps"], row["riskScore"],
                    row["riskAttitude"], row["householdType"], json.dumps(row["lifestyleTags"], ensure_ascii=False),
                    row["financialGoal"], row["moneyWorry"], row["joinedAt"], row["sourceDataRange"],
                    row["sourceDataAsOf"], row["synthetic"],
                )
                for row in personas
            ),
        )
    )

    operations.extend(
        [
            _activity_seed(input_dir, release),
            _product_seed(input_dir, release),
            _holding_seed(input_dir, release),
            _trade_seed(input_dir, release),
            _cosmetic_seed(input_dir),
            _l3_snapshot_prune_seed(release),
            _l3_seed(input_dir, release),
            _runtime_persona_projection_seed(input_dir, release, personas),
            _runtime_feature_projection_seed(input_dir, release),
            _runtime_routine_projection_seed(input_dir, release),
        ]
    )
    _validate_operation_counts(manifest, operations)
    return operations


def load_export_to_postgres(
    input_dir: Path,
    database_url: str,
    *,
    connector: Any | None = None,
) -> dict[str, int]:
    if connector is None:
        try:
            import psycopg  # type: ignore
        except ImportError as exception:
            raise RuntimeError(
                "psycopg is required for PostgreSQL loading; install tools/finmate_data_import/requirements.txt"
            ) from exception
        connector = psycopg.connect
    operations = build_seed_operations(input_dir)
    connection = connector(database_url)
    try:
        with connection:
            with connection.cursor() as cursor:
                for operation in operations:
                    if operation.rows:
                        cursor.executemany(operation.sql, operation.rows)
    finally:
        connection.close()
    return {operation.name: len(operation.rows) for operation in operations}


def runtime_status(database_url: str, *, connector: Any | None = None) -> dict[str, Any]:
    if connector is None:
        try:
            import psycopg  # type: ignore
        except ImportError as exception:
            raise RuntimeError(
                "psycopg is required for runtime verification; install tools/finmate_data_import/requirements.txt"
            ) from exception
        connector = psycopg.connect
    connection = connector(database_url)
    try:
        with connection:
            with connection.cursor() as cursor:
                cursor.execute("""
                    SELECT release_version, bundle_source_commit, l3_source_commit, l3_tree_sha256,
                           imported_at
                    FROM finmate_dataset_release WHERE release_version = %s
                """, (RELEASE_VERSION,))
                release = cursor.fetchone()
                if release is None:
                    raise ValueError("locked synthetic dataset release is not loaded")
                count_queries = {
                    "personaCount": "SELECT count(*) FROM finmate_import_persona",
                    "financialActivityCount": "SELECT count(*) FROM finmate_financial_activity",
                    "runtimeL3Count": "SELECT count(*) FROM finmate_import_l3_record",
                    "runtimePersonaCount": "SELECT count(*) FROM finmate_synthetic_runtime_persona",
                    "runtimeFeatureCount": "SELECT count(*) FROM finmate_synthetic_runtime_feature_profile",
                    "runtimeRoutineCount": "SELECT count(*) FROM finmate_synthetic_runtime_routine",
                    "insufficientPersonaCount": "SELECT count(*) FROM finmate_synthetic_runtime_persona WHERE data_state = 'INSUFFICIENT'",
                    "exactValuePersonaCount": "SELECT count(*) FROM finmate_synthetic_runtime_persona WHERE exact_values OR visible_fields <> '[]'",
                }
                counts: dict[str, int] = {}
                for field, query in count_queries.items():
                    cursor.execute(query)
                    counts[field] = int(cursor.fetchone()[0])
                cursor.execute("SELECT DISTINCT projection_version FROM finmate_synthetic_runtime_persona ORDER BY 1")
                projection_versions = [str(row[0]) for row in cursor.fetchall()]
    finally:
        connection.close()
    return {
        "releaseVersion": str(release[0]),
        "bundleSourceCommit": str(release[1]),
        "l3SourceCommit": str(release[2]),
        "l3TreeSha256": str(release[3]),
        "lastSuccessfulImportAt": release[4].isoformat(),
        **counts,
        "projectionVersions": projection_versions,
    }


def validate_runtime_status(status: Mapping[str, Any]) -> None:
    expected = {
        "releaseVersion": RELEASE_VERSION,
        "bundleSourceCommit": BUNDLE_SOURCE_COMMIT,
        "l3SourceCommit": L3_SOURCE_COMMIT,
        "l3TreeSha256": EXPECTED_L3_TREE_SHA256,
        "personaCount": 2_000,
        "financialActivityCount": 887_002,
        "runtimeL3Count": 845_202,
        "runtimePersonaCount": 2_000,
        "runtimeFeatureCount": 2_000,
        "runtimeRoutineCount": 3_939,
        "insufficientPersonaCount": 141,
        "exactValuePersonaCount": 0,
        "projectionVersions": [RUNTIME_PROJECTION_VERSION],
    }
    mismatches = {
        field: {"expected": expected_value, "actual": status.get(field)}
        for field, expected_value in expected.items()
        if status.get(field) != expected_value
    }
    if mismatches:
        raise ValueError("runtime projection verification failed: " + json.dumps(mismatches, sort_keys=True))


def _activity_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = _read_ndjson(input_dir / "financial_activities.ndjson")
    return SeedOperation(
        "financial_activities",
        """
        INSERT INTO finmate_financial_activity
            (source_transaction_id, source_persona_id, release_version, activity_type, direction, classification,
             category, subcategory, display_label, amount_krw, currency, occurred_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_transaction_id) DO UPDATE SET
            source_persona_id = EXCLUDED.source_persona_id,
            release_version = EXCLUDED.release_version,
            activity_type = EXCLUDED.activity_type,
            direction = EXCLUDED.direction,
            classification = EXCLUDED.classification,
            category = EXCLUDED.category,
            subcategory = EXCLUDED.subcategory,
            display_label = EXCLUDED.display_label,
            amount_krw = EXCLUDED.amount_krw,
            currency = EXCLUDED.currency,
            occurred_at = EXCLUDED.occurred_at
        """,
        tuple(
            (
                row["sourceTransactionId"], row["personaId"], release, row["activityType"], row["direction"],
                row["classification"], row["category"], row["subcategory"], row["displayLabel"],
                row["amountKrw"], row["currency"], row["occurredAt"],
            )
            for row in rows
        ),
    )


def _product_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = _read_ndjson(input_dir / "financial_products.ndjson")
    return SeedOperation(
        "financial_products",
        """
        INSERT INTO finmate_import_financial_product
            (holding_id, source_persona_id, release_version, product_type, product_name, holding_status, as_of_date, synthetic)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (holding_id) DO UPDATE SET
            release_version = EXCLUDED.release_version,
            product_type = EXCLUDED.product_type,
            product_name = EXCLUDED.product_name,
            holding_status = EXCLUDED.holding_status,
            as_of_date = EXCLUDED.as_of_date,
            synthetic = EXCLUDED.synthetic
        """,
        tuple(
            (row["holdingId"], row["personaId"], release, row["productType"], row["productName"],
             row["holdingStatus"], row["asOfDate"], row["synthetic"])
            for row in rows
        ),
    )


def _holding_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = _read_ndjson(input_dir / "investment_holdings.ndjson")
    return SeedOperation(
        "investment_holdings",
        """
        INSERT INTO finmate_import_investment_holding
            (holding_id, source_persona_id, release_version, product_name, ticker, category, purchase_amount_krw,
             evaluation_amount_krw, quantity, currency, as_of_date, synthetic)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (holding_id) DO UPDATE SET
            release_version = EXCLUDED.release_version,
            product_name = EXCLUDED.product_name,
            ticker = EXCLUDED.ticker,
            category = EXCLUDED.category,
            purchase_amount_krw = EXCLUDED.purchase_amount_krw,
            evaluation_amount_krw = EXCLUDED.evaluation_amount_krw,
            quantity = EXCLUDED.quantity,
            currency = EXCLUDED.currency,
            as_of_date = EXCLUDED.as_of_date,
            synthetic = EXCLUDED.synthetic
        """,
        tuple(
            (row["holdingId"], row["personaId"], release, row["productName"], row["ticker"], row["category"],
             row["purchaseAmountKrw"], row["evaluationAmountKrw"], row["quantity"], row["currency"],
             row["asOfDate"], row["synthetic"])
            for row in rows
        ),
    )


def _trade_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = _read_ndjson(input_dir / "investment_trades.ndjson")
    return SeedOperation(
        "investment_trades",
        """
        INSERT INTO finmate_import_investment_trade
            (trade_id, source_persona_id, release_version, product_name, ticker, action, quantity,
             gross_amount_krw, settlement_amount_krw, currency, occurred_at, synthetic)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (trade_id) DO UPDATE SET
            release_version = EXCLUDED.release_version,
            product_name = EXCLUDED.product_name,
            ticker = EXCLUDED.ticker,
            action = EXCLUDED.action,
            quantity = EXCLUDED.quantity,
            gross_amount_krw = EXCLUDED.gross_amount_krw,
            settlement_amount_krw = EXCLUDED.settlement_amount_krw,
            currency = EXCLUDED.currency,
            occurred_at = EXCLUDED.occurred_at,
            synthetic = EXCLUDED.synthetic
        """,
        tuple(
            (row["tradeId"], row["personaId"], release, row["productName"], row["ticker"], row["action"],
             row["quantity"], row["grossAmountKrw"], row["settlementAmountKrw"], row["currency"],
             row["occurredAt"], row["synthetic"])
            for row in rows
        ),
    )


def _cosmetic_seed(input_dir: Path) -> SeedOperation:
    rows = _read_ndjson(input_dir / "cosmetic_catalog.ndjson")
    return SeedOperation(
        "cosmetic_catalog",
        """
        INSERT INTO finmate_cosmetic_catalog
            (id, item_type, name, description, price_points, available, display_order)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET
            item_type = EXCLUDED.item_type,
            name = EXCLUDED.name,
            description = EXCLUDED.description,
            price_points = EXCLUDED.price_points,
            available = EXCLUDED.available,
            display_order = EXCLUDED.display_order
        """,
        tuple(
            (
                row["itemId"], row["category"], row["name"], row["description"],
                row["costPoints"], row["available"], row["displayOrder"],
            )
            for row in rows
        ),
    )


def _l3_seed(input_dir: Path, release: str) -> SeedOperation:
    rows: list[tuple[Any, ...]] = []
    l3_dir = input_dir / "l3"
    if l3_dir.exists():
        for path in sorted(l3_dir.glob("*.ndjson")):
            table_name = path.stem
            for row in _read_ndjson(path):
                canonical = _canonical_row(row)
                natural_key = hashlib.sha256(f"{table_name}|{canonical}".encode()).hexdigest()
                persona_id = next(
                    (row.get(key) for key in ("personaId", "persona_id", "viewer_persona_id", "persona_a") if row.get(key)),
                    None,
                )
                rows.append((table_name, natural_key, persona_id, release, canonical))
    return SeedOperation(
        "runtime_l3_records",
        """
        INSERT INTO finmate_import_l3_record
            (table_name, natural_key, source_persona_id, release_version, payload, imported_at)
        VALUES (%s, %s, %s, %s, %s::jsonb, CURRENT_TIMESTAMP)
        ON CONFLICT (table_name, natural_key) DO UPDATE SET
            source_persona_id = EXCLUDED.source_persona_id,
            release_version = EXCLUDED.release_version,
            payload = EXCLUDED.payload,
            imported_at = CURRENT_TIMESTAMP
        """,
        tuple(rows),
    )


def _l3_snapshot_prune_seed(release: str) -> SeedOperation:
    return SeedOperation(
        "runtime_l3_snapshot_prune",
        "DELETE FROM finmate_import_l3_record WHERE release_version = %s",
        ((release,),),
    )


def _runtime_persona_projection_seed(input_dir: Path, release: str, personas: list[dict[str, Any]]) -> SeedOperation:
    privacy = {
        str(row["persona_id"]): row
        for row in _read_ndjson(input_dir / "l3" / "privacy_settings.ndjson")
        if row.get("persona_id")
    }
    latest_features = _latest_feature_rows(input_dir)
    rows: list[tuple[Any, ...]] = []
    for persona in personas:
        persona_id = str(persona["personaId"])
        feature = latest_features.get(persona_id)
        if feature is None:
            continue
        visibility = str(privacy.get(persona_id, {}).get("friend_compare_visibility", "private")).lower()
        rows.append((
            persona_id, release, RUNTIME_PROJECTION_VERSION, _runtime_age_band(feature.get("age")), str(persona["cohort"]),
            _runtime_occupation_group(str(persona["archetype"])), _income_band(int(persona["monthlyIncomeKrw"])),
            _spending_tendency(feature.get("consumption_rate_c_bps")),
            _saving_rate_band(feature.get("saving_rate_c_bps")),
            _investment_tendency(str(persona["riskAttitude"])), _income_regularity(str(persona["incomeRegularity"])),
            _household_type(str(persona["householdType"])), json.dumps(persona["lifestyleTags"], ensure_ascii=False),
            _money_concern(str(persona["moneyWorry"])), visibility != "private", _runtime_data_state(feature),
            persona["sourceDataAsOf"], "[]", False,
        ))
    return SeedOperation(
        "runtime_persona_projection",
        """
        INSERT INTO finmate_synthetic_runtime_persona
            (source_persona_id, release_version, projection_version, age_band, cohort, occupation_group, income_band,
             spending_tendency, saving_rate_band, investment_tendency, income_regularity, household_type,
             lifestyle_tags, money_worry, peer_discovery_opt_in, data_state, last_synced_at, visible_fields, exact_values)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb::text, %s, %s, %s, %s::date, %s, %s)
        ON CONFLICT (source_persona_id, release_version) DO UPDATE SET
            projection_version = EXCLUDED.projection_version,
            age_band = EXCLUDED.age_band,
            cohort = EXCLUDED.cohort,
            occupation_group = EXCLUDED.occupation_group,
            income_band = EXCLUDED.income_band,
            spending_tendency = EXCLUDED.spending_tendency,
            saving_rate_band = EXCLUDED.saving_rate_band,
            investment_tendency = EXCLUDED.investment_tendency,
            income_regularity = EXCLUDED.income_regularity,
            household_type = EXCLUDED.household_type,
            lifestyle_tags = EXCLUDED.lifestyle_tags,
            money_worry = EXCLUDED.money_worry,
            peer_discovery_opt_in = EXCLUDED.peer_discovery_opt_in,
            data_state = EXCLUDED.data_state,
            last_synced_at = EXCLUDED.last_synced_at,
            visible_fields = EXCLUDED.visible_fields,
            exact_values = FALSE
        """,
        tuple(rows),
    )


def _runtime_feature_projection_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = []
    for persona_id, feature in _latest_feature_rows(input_dir).items():
        rows.append((
            persona_id, release, RUNTIME_PROJECTION_VERSION, _runtime_month_start(feature["month"]), feature.get("age"), feature.get("cohort"),
            feature.get("income_norm_bps"), feature.get("essential_ratio_bps"),
            feature.get("consumption_rate_c_bps"), feature.get("saving_rate_c_bps"),
            feature.get("invest_rate_c_bps"), feature.get("defense_score_bps"),
            feature.get("saving_score_bps"), feature.get("invest_score_bps"), feature.get("cluster_id"),
        ))
    return SeedOperation(
        "runtime_feature_projection",
        """
        INSERT INTO finmate_synthetic_runtime_feature_profile
            (source_persona_id, release_version, projection_version, feature_month, age, cohort, income_norm_bps, essential_ratio_bps,
             consumption_rate_bps, saving_rate_bps, invest_rate_bps, defense_score_bps, saving_score_bps,
             invest_score_bps, lifestyle_cluster_id)
        VALUES (%s, %s, %s, %s::date, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_persona_id, release_version) DO UPDATE SET
            projection_version = EXCLUDED.projection_version,
            feature_month = EXCLUDED.feature_month,
            age = EXCLUDED.age,
            cohort = EXCLUDED.cohort,
            income_norm_bps = EXCLUDED.income_norm_bps,
            essential_ratio_bps = EXCLUDED.essential_ratio_bps,
            consumption_rate_bps = EXCLUDED.consumption_rate_bps,
            saving_rate_bps = EXCLUDED.saving_rate_bps,
            invest_rate_bps = EXCLUDED.invest_rate_bps,
            defense_score_bps = EXCLUDED.defense_score_bps,
            saving_score_bps = EXCLUDED.saving_score_bps,
            invest_score_bps = EXCLUDED.invest_score_bps,
            lifestyle_cluster_id = EXCLUDED.lifestyle_cluster_id
        """,
        tuple(rows),
    )


def _runtime_routine_projection_seed(input_dir: Path, release: str) -> SeedOperation:
    rows = []
    for row in _read_ndjson(input_dir / "l3" / "routine_summaries.ndjson"):
        routine = str(row.get("routine", ""))
        domain = _approved_routine_domain(routine)
        if domain is None or not row.get("persona_id"):
            continue
        rows.append((
            str(row["persona_id"]), release, RUNTIME_PROJECTION_VERSION, routine, domain, row.get("frequency"), row.get("ratio_pct_bps"),
            row.get("maintained_months"),
        ))
    return SeedOperation(
        "runtime_routine_projection",
        """
        INSERT INTO finmate_synthetic_runtime_routine
            (source_persona_id, release_version, projection_version, source_routine, domain, frequency, ratio_bps, maintained_months)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_persona_id, release_version, source_routine) DO UPDATE SET
            projection_version = EXCLUDED.projection_version,
            domain = EXCLUDED.domain,
            frequency = EXCLUDED.frequency,
            ratio_bps = EXCLUDED.ratio_bps,
            maintained_months = EXCLUDED.maintained_months
        """,
        tuple(rows),
    )


def _latest_feature_rows(input_dir: Path) -> dict[str, dict[str, Any]]:
    latest: dict[str, dict[str, Any]] = {}
    for row in _read_ndjson(input_dir / "l3" / "features.ndjson"):
        persona_id = row.get("persona_id")
        month = row.get("month")
        if not persona_id or not month:
            continue
        key = str(persona_id)
        if key not in latest or str(month) > str(latest[key]["month"]):
            latest[key] = row
    return latest


def _runtime_month_start(value: Any) -> str:
    month = str(value)
    if len(month) == 7:
        return month + "-01"
    return month


def _runtime_age_band(age: Any) -> str:
    value = int(age)
    if 19 <= value <= 23:
        return "AGE_19_23"
    if 24 <= value <= 29:
        return "AGE_24_29"
    return "AGE_30_34"


def _runtime_occupation_group(archetype: str) -> str:
    if "대학생/알바" in archetype:
        return "STUDENT"
    if "취준" in archetype or "지원" in archetype:
        return "JOB_SEEKER"
    if "프리랜서" in archetype or "크리에이터" in archetype:
        return "FREELANCER"
    return "EARLY_CAREER"


def _income_band(monthly_income_krw: int) -> str:
    if monthly_income_krw == 0:
        return "NONE"
    if monthly_income_krw < 2_000_000:
        return "UNDER_200"
    if monthly_income_krw < 3_000_000:
        return "FROM_200_TO_300"
    return "OVER_300"


def _spending_tendency(consumption_rate_bps: Any) -> str:
    if consumption_rate_bps is None:
        return "UNKNOWN"
    value = int(consumption_rate_bps)
    if value < 6000:
        return "PLANNED"
    if value < 8500:
        return "BALANCED"
    return "VARIABLE"


def _saving_rate_band(saving_rate_bps: Any) -> str:
    if saving_rate_bps is None:
        return "UNKNOWN"
    value = int(saving_rate_bps)
    if value < 1000:
        return "UNDER_10"
    if value < 2000:
        return "FROM_10_TO_20"
    return "OVER_20"


def _runtime_data_state(feature: Mapping[str, Any]) -> str:
    required = (
        "consumption_rate_c_bps",
        "saving_rate_c_bps",
        "defense_score_bps",
        "saving_score_bps",
        "invest_score_bps",
    )
    return "FRESH" if all(feature.get(field) is not None for field in required) else "INSUFFICIENT"


def _investment_tendency(risk_attitude: str) -> str:
    if risk_attitude in {"원금보전형", "안정추구형"}:
        return "CAUTIOUS"
    if risk_attitude == "중립형":
        return "BALANCED"
    return "LEARNING"


def _income_regularity(value: str) -> str:
    return {"규칙적": "REGULAR", "REGULAR": "REGULAR", "불규칙": "IRREGULAR", "IRREGULAR": "IRREGULAR"}.get(value, "NONE")


def _household_type(value: str) -> str:
    if any(token in value for token in ("부모", "가족")):
        return "WITH_FAMILY"
    if "기숙사" in value:
        return "DORMITORY"
    if any(token in value for token in ("월세", "전세", "자취")):
        return "RENT"
    return "OTHER"


def _money_concern(value: str) -> str:
    if "소비" in value or "과소비" in value:
        return "SPENDING"
    if "비상" in value:
        return "EMERGENCY_FUND"
    if "투자" in value:
        return "INVESTMENT_JUDGMENT"
    if "저축" in value:
        return "SAVING"
    return "UNSURE"


def _approved_routine_domain(routine: str) -> str | None:
    normalized = routine.strip().casefold().replace(" ", "_")
    if normalized in {"자동저축", "automatic_saving"}:
        return "SAVING"
    if normalized in {"카페방문", "카페_방문", "cafe_visit"}:
        return "SPENDING"
    return None


def _read_ndjson(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise ValueError(f"required import file is missing: {path.name}")
    values = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if line.strip():
                try:
                    values.append(json.loads(line))
                except json.JSONDecodeError as exception:
                    raise ValueError(f"invalid NDJSON in {path.name}:{line_number}") from exception
    return values


def _validate_operation_counts(manifest: Mapping[str, Any], operations: list[SeedOperation]) -> None:
    expected = manifest["counts"]
    actual = {operation.name: len(operation.rows) for operation in operations}
    mapping = {
        "personas": "personas",
        "financialActivities": "financial_activities",
        "financialProducts": "financial_products",
        "investmentHoldings": "investment_holdings",
        "investmentTrades": "investment_trades",
        "cosmeticCatalogItems": "cosmetic_catalog",
    }
    for manifest_name, operation_name in mapping.items():
        if int(expected[manifest_name]) != actual[operation_name]:
            raise ValueError(
                f"import count mismatch for {manifest_name}: expected {expected[manifest_name]}, got {actual[operation_name]}"
            )
    expected_runtime_l3 = sum(int(value) for value in manifest["runtimeL3Counts"].values())
    if expected_runtime_l3 != actual["runtime_l3_records"]:
        raise ValueError(
            "runtime L3 row count mismatch: "
            f"expected {expected_runtime_l3}, got {actual['runtime_l3_records']}"
        )


def _validate_l3_export(input_dir: Path, manifest: Mapping[str, Any]) -> None:
    policy = DatasetImportPolicy()
    decisions = (
        ("runtime", "runtimeL3Counts", input_dir / "l3", policy.runtime_l3_tables),
        ("golden", "goldenL3Counts", input_dir / "golden_l3", policy.golden_l3_tables),
    )
    for label, manifest_key, directory, expected_tables in decisions:
        counts = manifest.get(manifest_key)
        if not isinstance(counts, dict) or set(counts) != set(expected_tables):
            raise ValueError(f"{label} L3 manifest table set does not match the locked policy")
        actual_tables = {path.stem for path in directory.glob("*.ndjson")} if directory.exists() else set()
        if actual_tables != set(expected_tables):
            raise ValueError(f"{label} L3 file set does not match the locked policy")
        for table_name in sorted(expected_tables):
            actual_count = len(_read_ndjson(directory / f"{table_name}.ndjson"))
            expected_count = int(counts[table_name])
            if actual_count != expected_count:
                raise ValueError(
                    f"{label} L3 row count mismatch for {table_name}: "
                    f"expected {expected_count}, got {actual_count}"
                )


def _validate_export_file_checksums(
    input_dir: Path,
    manifest: Mapping[str, Any],
    *,
    require_locked: bool,
) -> None:
    checksums = manifest.get("exportFileSha256")
    if not isinstance(checksums, dict) or set(checksums) != set(expected_export_file_paths()):
        raise ValueError("export file checksum set does not match the locked payload set")
    normalized = {str(path): str(checksum).lower() for path, checksum in checksums.items()}
    if require_locked and normalized != locked_export_file_sha256():
        raise ValueError("export file checksums do not match the locked release")
    for relative_path, expected_checksum in sorted(normalized.items()):
        path = input_dir / relative_path
        if not path.is_file():
            raise ValueError(f"required export payload is missing: {relative_path}")
        actual_checksum = sha256_file(path)
        if actual_checksum != expected_checksum:
            raise ValueError(
                f"export file checksum mismatch for {relative_path}: "
                f"expected {expected_checksum}, got {actual_checksum}"
            )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Import the locked FinMate synthetic-data release")
    subparsers = parser.add_subparsers(dest="command", required=True)
    export_parser = subparsers.add_parser("export", help="verify and transform the release")
    export_parser.add_argument("--source-root", type=Path, required=True, help="unpacked v1.0.0 bundle root")
    export_parser.add_argument(
        "--l3-source-root",
        type=Path,
        required=True,
        help="finmate-data checkout containing the locked latest data/l3 tree",
    )
    export_parser.add_argument("--archive", type=Path, required=True)
    export_parser.add_argument("--output-dir", type=Path, required=True)
    load_parser = subparsers.add_parser("load", help="upsert a transformed seed into PostgreSQL")
    load_parser.add_argument("--input-dir", type=Path, required=True)
    load_parser.add_argument("--database-url", required=True)
    verify_parser = subparsers.add_parser("verify-runtime", help="verify locked runtime projection counts and provenance")
    verify_parser.add_argument("--database-url", required=True)
    bootstrap_parser = subparsers.add_parser("bootstrap", help="verify, export, load, and validate the locked runtime dataset")
    bootstrap_parser.add_argument("--source-root", type=Path, required=True)
    bootstrap_parser.add_argument("--l3-source-root", type=Path, required=True)
    bootstrap_parser.add_argument("--archive", type=Path, required=True)
    bootstrap_parser.add_argument("--output-dir", type=Path, required=True)
    bootstrap_parser.add_argument("--database-url", required=True)
    args = parser.parse_args(argv)

    if args.command in {"export", "bootstrap"}:
        release = export_release(
            args.source_root,
            args.output_dir,
            args.archive,
            l3_source_root=args.l3_source_root,
        )
        print(f"exported {release.release_version} to {args.output_dir}")
        if args.command == "export":
            return 0
        counts = load_export_to_postgres(args.output_dir, args.database_url)
        status = runtime_status(args.database_url)
        validate_runtime_status(status)
        print(json.dumps({"loadCounts": counts, "runtimeStatus": status}, ensure_ascii=False, sort_keys=True))
        return 0
    if args.command == "verify-runtime":
        status = runtime_status(args.database_url)
        validate_runtime_status(status)
        print(json.dumps(status, ensure_ascii=False, sort_keys=True))
        return 0
    counts = load_export_to_postgres(args.input_dir, args.database_url)
    print(json.dumps(counts, ensure_ascii=False, sort_keys=True))
    return 0


def _write_ndjson(path: Path, items: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for item in items:
            output.write(json.dumps(item, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
            output.write("\n")


if __name__ == "__main__":
    raise SystemExit(main())
