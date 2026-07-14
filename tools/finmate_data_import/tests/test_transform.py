from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


PACKAGE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE_DIR))

import finmate_import as importer  # noqa: E402

from finmate_import import (  # noqa: E402
    DatasetImportPolicy,
    DatasetReleaseManifest,
    build_seed_operations,
    cosmetic_catalog,
    export_golden_l3,
    export_release,
    export_runtime_l3,
    is_allowed_quest_template,
    ratio_to_bps,
    sanitize_ledger_row,
    sanitize_golden_l3_row,
    sanitize_l3_row,
    sanitize_investment_holding,
    sanitize_investment_trade,
    sanitize_manifest_products,
    sanitize_point_row,
    sanitize_profile,
    sanitize_quest_row,
    score_to_bps,
    sha256_file,
)


class DatasetReleaseManifestTest(unittest.TestCase):
    def test_postgres_loader_does_not_expose_checksum_bypass(self) -> None:
        with self.assertRaises(TypeError):
            importer.load_export_to_postgres(
                Path("missing-export"),
                "postgresql://unused",
                allow_unverified=True,
                connector=lambda _url: self.fail("unverified load reached the database connector"),
            )

    def test_release_manifest_uses_locked_v1_metadata(self) -> None:
        manifest = DatasetReleaseManifest.from_generation_manifest(
            {
                "seed": 20260713,
                "demo_date": "2026-07-13",
                "data_range": "2026-01~2026-07",
                "persona_count": 2000,
            }
        )

        self.assertEqual(manifest.release_version, "v1.0.0")
        self.assertEqual(manifest.seed, 20260713)
        self.assertEqual(manifest.data_start, "2026-01-01")
        self.assertEqual(manifest.data_end, "2026-07-13")
        self.assertEqual(manifest.persona_count, 2000)

    def test_sha256_is_streamed_and_lowercase(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "release.tar.zst"
            path.write_bytes(b"finmate-data-v1")

            self.assertEqual(sha256_file(path), hashlib.sha256(path.read_bytes()).hexdigest())

    def test_bundle_and_l3_provenance_are_locked_separately(self) -> None:
        self.assertEqual(
            getattr(importer, "BUNDLE_SOURCE_COMMIT", None),
            "63ca3d046eba9ec510e377a28a0083233aefff61",
        )
        self.assertEqual(
            getattr(importer, "L3_SOURCE_COMMIT", None),
            "22243bce34131737fc762675f0817ead08bc165a",
        )
        self.assertEqual(
            getattr(importer, "EXPECTED_L3_TREE_SHA256", None),
            "dd30ae91f5e517f2a502ce46b2dfe3666107562e2dc52edb25fec48b893c3c33",
        )

    def test_locked_export_payload_has_an_exact_checksum_for_every_file(self) -> None:
        expected_paths = importer.expected_export_file_paths()
        checksums = importer.locked_export_file_sha256()

        self.assertEqual(len(expected_paths), 27)
        self.assertEqual(set(checksums), set(expected_paths))
        self.assertEqual(
            checksums["l3/friend_group_stats.ndjson"],
            "aa45b0baac83cacbd4d4b304ffc8afcbe8e2f4b8333dd858a956ddc3502d12e3",
        )


class DatasetImportPolicyTest(unittest.TestCase):
    def test_runtime_golden_and_excluded_tables_are_disjoint(self) -> None:
        policy = DatasetImportPolicy()

        self.assertFalse(policy.runtime_l3_tables & policy.golden_l3_tables)
        self.assertFalse(policy.runtime_l3_tables & policy.excluded_l3_tables)
        self.assertFalse(policy.golden_l3_tables & policy.excluded_l3_tables)
        self.assertIn("routine_summaries", policy.runtime_l3_tables)
        self.assertIn("metrics_monthly", policy.golden_l3_tables)
        self.assertIn("reward_boxes", policy.excluded_l3_tables)
        self.assertIn("point_catalog", policy.excluded_l3_tables)

    def test_every_v1_l3_table_has_exactly_one_import_decision(self) -> None:
        policy = DatasetImportPolicy()
        source_tables = {
            "active_goals", "app_sessions", "balance_monthly", "bookmarks", "boss_instances",
            "budgets_daily", "budgets_monthly", "cluster_profiles", "features", "feed_events",
            "friend_group_stats", "friendships", "goal_candidates", "goal_history", "level_rules",
            "metrics_monthly", "point_catalog", "point_ledger", "privacy_settings", "quest_log",
            "quiz_log", "raid_log", "retrospectives", "reward_boxes", "routine_summaries", "stats",
            "stats_history", "streaks", "sync_events", "title_catalog", "titles_earned",
        }

        self.assertEqual(
            policy.runtime_l3_tables | policy.golden_l3_tables | policy.excluded_l3_tables,
            source_tables,
        )
        self.assertEqual(len(policy.runtime_l3_tables), 16)
        self.assertEqual(len(policy.golden_l3_tables), 5)
        self.assertEqual(len(policy.excluded_l3_tables), 10)
        self.assertIn("balance_monthly", policy.golden_l3_tables)
        self.assertIn("budgets_monthly", policy.golden_l3_tables)

    def test_unsafe_investment_quests_are_rejected(self) -> None:
        self.assertFalse(is_allowed_quest_template("QUEST-SUB-STOCK-ACCOUNT"))
        self.assertFalse(is_allowed_quest_template("QUEST-INVEST-FIRST-BUY"))
        self.assertTrue(is_allowed_quest_template("QUEST-INVEST-RISK-CHECK"))

    def test_runtime_l3_export_uses_allowlist_and_filters_unsafe_rows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            l3 = root / "source" / "data" / "l3"
            l3.mkdir(parents=True)
            for name in ("friend_group_stats", "quest_log", "point_ledger"):
                (l3 / f"{name}.parquet").touch()
            rows = {
                "friend_group_stats": [
                    {
                        "persona_id": "P0001",
                        "friend_count": 12,
                        "scored_friend_count": 11,
                        "locked": False,
                        "avg_defense_score": 72,
                        "avg_saving_score": 68,
                        "avg_invest_score": 51,
                        "top_savings_product": "must be removed",
                    }
                ],
                "quest_log": [
                    {
                        "persona_id": "P0001",
                        "quest_instance_id": "safe",
                        "template_id": "QUEST-INVEST-RISK-CHECK",
                        "category": "투자",
                        "title": "위험성향 확인",
                        "assigned_date": "2026-07-10",
                        "due_date": "2026-07-13",
                        "status": "완료",
                        "source": "recommended",
                        "evidence_source": "app_event",
                        "current_value": 1,
                        "target_value": 1,
                        "xp": 10,
                        "point": 5,
                    },
                    {
                        "persona_id": "P0001",
                        "quest_instance_id": "unsafe",
                        "template_id": "QUEST-INVEST-FIRST-BUY",
                        "category": "투자",
                        "title": "첫 매수",
                        "assigned_date": "2026-07-10",
                        "due_date": "2026-07-13",
                        "status": "완료",
                        "source": "recommended",
                        "evidence_source": "ledger",
                        "current_value": 1,
                        "target_value": 1,
                        "xp": 80,
                        "point": 100,
                    },
                ],
                "point_ledger": [
                    {
                        "persona_id": "P0001",
                        "date": "2026-07-13",
                        "delta": 5,
                        "reason": "quest_complete:QUEST-INVEST-RISK-CHECK",
                        "event_type": "earn",
                    }
                ],
            }

            counts = export_runtime_l3(
                root / "source",
                root / "output",
                table_names=frozenset(rows),
                row_reader=lambda path: rows[path.stem],
            )

            self.assertEqual(counts, {"friend_group_stats": 1, "point_ledger": 1, "quest_log": 1})
            exported = (root / "output" / "l3" / "quest_log.ndjson").read_text(encoding="utf-8")
            self.assertIn("QUEST-INVEST-RISK-CHECK", exported)
            self.assertNotIn("QUEST-INVEST-FIRST-BUY", exported)
            group = (root / "output" / "l3" / "friend_group_stats.ndjson").read_text(encoding="utf-8")
            self.assertIn('"scored_friend_count":11', group)
            self.assertNotIn("must be removed", group)

    def test_l3_tree_verification_rejects_missing_extra_and_modified_files(self) -> None:
        calculate = getattr(importer, "l3_tree_sha256", None)
        verify = getattr(importer, "verify_l3_source", None)
        self.assertTrue(callable(calculate))
        self.assertTrue(callable(verify))

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            l3 = root / "data" / "l3"
            l3.mkdir(parents=True)
            policy = DatasetImportPolicy()
            all_tables = policy.runtime_l3_tables | policy.golden_l3_tables | policy.excluded_l3_tables
            for table_name in all_tables:
                (l3 / f"{table_name}.parquet").write_bytes(table_name.encode("utf-8"))
            (l3 / "goal_templates.json").write_text('{"ignored":true}', encoding="utf-8")

            expected = "a7d429a0ab4ae62872598ca83e143c2c2bb15ee4dbad98ecf2de34d86a592e27"
            self.assertEqual(calculate(root), expected)
            self.assertEqual(verify(root, expected_sha256=expected), expected)

            (l3 / "features.parquet").write_bytes(b"modified")
            with self.assertRaisesRegex(ValueError, "L3 tree checksum"):
                verify(root, expected_sha256=expected)

            (l3 / "features.parquet").unlink()
            with self.assertRaisesRegex(ValueError, "L3 parquet file set"):
                calculate(root)

            (l3 / "features.parquet").write_bytes(b"features")
            (l3 / "unexpected.parquet").write_bytes(b"unexpected")
            with self.assertRaisesRegex(ValueError, "L3 parquet file set"):
                calculate(root)

    def test_golden_l3_is_exported_separately_and_preserves_signed_comparison_values(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            l3 = root / "source" / "data" / "l3"
            l3.mkdir(parents=True)
            for name in ("balance_monthly", "budgets_monthly"):
                (l3 / f"{name}.parquet").touch()
            rows = {
                "balance_monthly": [
                    {
                        "persona_id": "P0001",
                        "month": "2026-03",
                        "month_net_change_krw": -253501.0,
                        "cumulative_net_asset_change_krw": 389194.0,
                    }
                ],
                "budgets_monthly": [
                    {
                        "persona_id": "P0001",
                        "month": "2026-03",
                        "month_budget_krw": 1240000.0,
                        "month_spend_krw": 1366164.0,
                        "usage_rate": 1.101745,
                    }
                ],
            }

            counts = export_golden_l3(
                root / "source",
                root / "output",
                table_names=frozenset(rows),
                row_reader=lambda path: rows[path.stem],
            )

            self.assertEqual(counts, {"balance_monthly": 1, "budgets_monthly": 1})
            balance = json.loads(
                (root / "output" / "golden_l3" / "balance_monthly.ndjson").read_text(encoding="utf-8")
            )
            budget = json.loads(
                (root / "output" / "golden_l3" / "budgets_monthly.ndjson").read_text(encoding="utf-8")
            )
            self.assertEqual(balance["monthNetChangeKrw"], -253501)
            self.assertEqual(balance["cumulativeNetAssetChangeKrw"], 389194)
            self.assertEqual(budget["usageRateBps"], 10000)


class NormalizationTest(unittest.TestCase):
    def test_locked_group_hides_averages_and_undefined_golden_stats_stay_missing(self) -> None:
        locked = sanitize_l3_row(
            "friend_group_stats",
            {
                "persona_id": "P0001",
                "friend_count": 4.0,
                "scored_friend_count": 0.0,
                "locked": True,
                "avg_defense_score": 77,
                "avg_saving_score": 66,
                "avg_invest_score": 55,
            },
        )
        self.assertEqual(locked["friend_count"], 4)
        self.assertEqual(locked["scored_friend_count"], 0)
        self.assertNotIn("avg_defense_score_bps", locked)
        self.assertNotIn("avg_saving_score_bps", locked)
        self.assertNotIn("avg_invest_score_bps", locked)

        undefined = sanitize_golden_l3_row(
            "stats",
            {
                "persona_id": "P0001",
                "month": "2026-07",
                "defense_score": float("nan"),
                "saving_score": None,
                "invest_score": float("nan"),
                "data_sufficiency": "INSUFFICIENT",
            },
        )
        self.assertNotIn("defenseScoreBps", undefined)
        self.assertNotIn("savingScoreBps", undefined)
        self.assertNotIn("investScoreBps", undefined)
        self.assertEqual(undefined["dataSufficiency"], "INSUFFICIENT")

    def test_rates_and_scores_use_integer_basis_points(self) -> None:
        self.assertEqual(ratio_to_bps(0.075), 750)
        self.assertEqual(ratio_to_bps(1.2), 10000)
        self.assertEqual(score_to_bps(72.5), 7250)

    def test_profile_drops_name_region_and_income_components(self) -> None:
        normalized = sanitize_profile(
            {
                "persona_id": "P0001",
                "synthetic_name": "가상청년 P0001",
                "age": 21,
                "archetype": "대학생/알바",
                "cohort": "20s",
                "job": "대학생",
                "region": "대전 서구",
                "monthly_income_krw": 1200000,
                "income_regularity": "불규칙",
                "income_components": {"allowance_range": [500000, 500000]},
                "target_saving_rate": 0.075,
                "target_investment_rate": 0.0,
                "risk_score": 2,
                "risk_attitude": "안정추구형",
                "household_type": "쉐어하우스",
                "lifestyle_tags": ["간편결제선호", "카페선호"],
                "financial_goal": "등록금 완충",
                "money_worry": "과소비 걱정",
                "joined_at": "2026-07-13",
                "data_range": "2026-01~2026-07",
                "demo_date": "2026-07-13",
                "is_synthetic": True,
            }
        )

        self.assertEqual(normalized["personaId"], "P0001")
        self.assertEqual(normalized["ageBand"], "20-24")
        self.assertEqual(normalized["monthlyIncomeKrw"], 1200000)
        self.assertEqual(normalized["targetSavingRateBps"], 750)
        self.assertNotIn("syntheticName", normalized)
        self.assertNotIn("region", normalized)
        self.assertNotIn("incomeComponents", normalized)

    def test_ledger_drops_raw_memo_accounts_and_location_bearing_content(self) -> None:
        normalized = sanitize_ledger_row(
            {
                "날짜": "2026-07-13",
                "시간": "16:08",
                "타입": "지출",
                "대분류": "생활",
                "소분류": "편의점",
                "내용": "CU 정림동점",
                "금액": "-1800",
                "화폐": "KRW",
                "결제수단": "토스 간편결제",
                "메모": "집 앞",
                "persona_id": "P0001",
                "transaction_id": "P0001-T0441",
                "cashflow_bucket": "소비",
                "account_ref": "P0001-토스페이",
                "api_ref": "efinance/private.json#row",
                "rule_id": "calib:생활/편의점",
            }
        )

        self.assertEqual(normalized["activityType"], "SPENDING")
        self.assertEqual(normalized["direction"], "OUTFLOW")
        self.assertEqual(normalized["amountKrw"], 1800)
        self.assertEqual(normalized["displayLabel"], "편의점")
        self.assertEqual(normalized["occurredAt"], "2026-07-13T16:08:00+09:00")
        serialized = str(normalized)
        for secret in ("정림동", "집 앞", "P0001-토스페이", "private.json", "rule_id"):
            self.assertNotIn(secret, serialized)

    def test_ledger_accepts_actual_income_bucket_and_uses_pair_based_essential_rules(self) -> None:
        income = sanitize_ledger_row(
            {
                "날짜": "2026-07-09",
                "시간": "09:00",
                "대분류": "근로소득",
                "소분류": "월급",
                "금액": "2800000",
                "화폐": "KRW",
                "persona_id": "P0001",
                "transaction_id": "P0001-INCOME",
                "cashflow_bucket": "소득",
            }
        )
        transit = sanitize_ledger_row(
            {
                "날짜": "2026-07-10",
                "시간": "08:00",
                "대분류": "교통",
                "소분류": "대중교통",
                "금액": "-1500",
                "화폐": "KRW",
                "persona_id": "P0001",
                "transaction_id": "P0001-TRANSIT",
                "cashflow_bucket": "소비",
            }
        )
        taxi = sanitize_ledger_row(
            {
                "날짜": "2026-07-10",
                "시간": "23:00",
                "대분류": "교통",
                "소분류": "택시",
                "금액": "-12000",
                "화폐": "KRW",
                "persona_id": "P0001",
                "transaction_id": "P0001-TAXI",
                "cashflow_bucket": "소비",
            }
        )

        self.assertEqual(income["activityType"], "INCOME")
        self.assertEqual(income["classification"], "EARNED_INCOME")
        self.assertEqual(transit["classification"], "ESSENTIAL_EXPENSE")
        self.assertEqual(taxi["classification"], "DISCRETIONARY_EXPENSE")

    def test_cosmetic_catalog_is_deterministic_and_non_cash(self) -> None:
        items = cosmetic_catalog()

        self.assertEqual(
            [item["itemId"] for item in items],
            ["cosmetic-outfit-mint", "cosmetic-frame-sprout", "cosmetic-theme-daylight"],
        )
        self.assertEqual([item["costPoints"] for item in items], [5, 15, 25])
        self.assertEqual(len({item["itemId"] for item in items}), len(items))
        self.assertTrue(all(item["category"] in {"OUTFIT", "PROFILE_FRAME", "THEME"} for item in items))
        self.assertTrue(all(item["randomized"] is False for item in items))
        self.assertTrue(all(item["cashEquivalent"] is False for item in items))
        self.assertTrue(all(item["available"] is True for item in items))

    def test_product_and_investment_exports_never_include_accounts_or_raw_memos(self) -> None:
        products = sanitize_manifest_products(
            {
                "persona_id": "P0001",
                "savings_product": "하나 여행 적금",
                "housing_product": "주택청약종합저축",
                "invest_participation": True,
                "generated_at": "2026-07-13T23:59:59",
                "api_scope": ["금투-001"],
                "files": {"금투-001": {"file_name": "secret.json"}},
            }
        )
        holding = sanitize_investment_holding(
            "P0001",
            {
                "prod_name": "KODEX 200",
                "prod_code": "069500.KS",
                "prod_type": "201",
                "eval_amt": 1200000,
                "purchase_amt": 1100000,
                "holding_num": 8,
                "currency_code": "KRW",
                "account_num": "123-456",
            },
            "20260713",
        )
        trade = sanitize_investment_trade(
            "P0001",
            {
                "prod_name": "KODEX 200",
                "prod_code": "069500.KS",
                "Bod": "매수",
                "trans_num": 1,
                "trans_amt": 50000,
                "settle_amt": 50100,
                "trans_dtime": "20260708111000",
                "currency_code": "KRW",
                "trans_no": "STK-SECRET",
                "trans_memo": "KODEX 200 매수",
                "balance_amt": 999999,
            },
        )

        self.assertEqual([item["productName"] for item in products], ["하나 여행 적금", "주택청약종합저축"])
        self.assertEqual(holding["ticker"], "069500.KS")
        self.assertEqual(trade["action"], "BUY")
        serialized = str([products, holding, trade])
        for forbidden in ("api_scope", "files", "account_num", "trans_no", "trans_memo", "balance_amt", "123-456"):
            self.assertNotIn(forbidden, serialized)

    def test_l3_allowlist_drops_products_from_group_stats_and_normalizes_scores(self) -> None:
        normalized = sanitize_l3_row(
            "friend_group_stats",
            {
                "persona_id": "P0001",
                "friend_count": 12,
                "scored_friend_count": 11.0,
                "locked": False,
                "avg_defense_score": 72.5,
                "avg_saving_score": 64.0,
                "avg_invest_score": 51.0,
                "top_savings_product": "should-not-cross-boundary",
                "top_savings_product_share": 0.4,
            },
        )

        self.assertEqual(normalized["avg_defense_score_bps"], 7250)
        self.assertEqual(normalized["avg_saving_score_bps"], 6400)
        self.assertEqual(normalized["avg_invest_score_bps"], 5100)
        self.assertEqual(normalized["scored_friend_count"], 11)
        self.assertNotIn("top_savings_product", normalized)
        self.assertNotIn("top_savings_product_share", normalized)

    def test_unlocked_friend_group_requires_valid_scored_population_and_averages(self) -> None:
        base = {
            "persona_id": "P0001",
            "friend_count": 12,
            "scored_friend_count": 11,
            "locked": False,
            "avg_defense_score": 72.5,
            "avg_saving_score": 64.0,
            "avg_invest_score": 51.0,
        }

        with self.assertRaisesRegex(ValueError, "scored_friend_count"):
            sanitize_l3_row("friend_group_stats", {**base, "scored_friend_count": 13})
        with self.assertRaisesRegex(ValueError, "average scores"):
            sanitize_l3_row("friend_group_stats", {**base, "avg_invest_score": None})

    def test_unsafe_quest_and_its_point_event_are_removed(self) -> None:
        unsafe = {
            "persona_id": "P0001",
            "quest_instance_id": "quest-1",
            "template_id": "QUEST-INVEST-FIRST-BUY",
            "category": "투자",
            "title": "첫 투자 실행",
            "assigned_date": "2026-07-10",
            "due_date": "2026-07-13",
            "status": "completed",
            "source": "recommended",
            "evidence_source": "ledger",
            "current_value": 1,
            "target_value": 1,
            "xp": 80,
            "point": 100,
            "box": "gold",
            "completed_at": "2026-07-11",
            "linked_goal_id": "goal-1",
        }
        self.assertIsNone(sanitize_quest_row(unsafe))
        self.assertIsNone(
            sanitize_point_row(
                {
                    "persona_id": "P0001",
                    "date": "2026-07-11",
                    "delta": 100,
                    "reason": "quest_complete:QUEST-INVEST-FIRST-BUY",
                    "balance_after": 500,
                    "event_type": "earn",
                }
            )
        )

        safe = {**unsafe, "template_id": "QUEST-INVEST-RISK-CHECK", "title": "위험성향 확인", "box": "gold"}
        normalized = sanitize_quest_row(safe)
        self.assertEqual(normalized["templateId"], "QUEST-INVEST-RISK-CHECK")
        self.assertEqual(normalized["pointReward"], 100)
        self.assertNotIn("box", normalized)

    def test_runtime_projection_normalizes_search_enums_and_limits_routine_domains(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            l3 = root / "l3"
            l3.mkdir()
            (l3 / "privacy_settings.ndjson").write_text(
                json.dumps({"persona_id": "P0001", "friend_compare_visibility": "friends"}) + "\n",
                encoding="utf-8",
            )
            (l3 / "features.ndjson").write_text(
                "\n".join(
                    json.dumps(row)
                    for row in (
                        {"persona_id": "P0001", "month": "2026-06-01", "age": 23, "consumption_rate_c_bps": 5900, "saving_rate_c_bps": 900},
                        {"persona_id": "P0001", "month": "2026-07-01", "age": 24, "cohort": "20s", "income_norm_bps": 4000, "essential_ratio_bps": 3200, "consumption_rate_c_bps": 6000, "saving_rate_c_bps": 2000, "invest_rate_c_bps": 1000, "defense_score_bps": 7000, "saving_score_bps": 6500, "invest_score_bps": 5000, "cluster_id": "c-1"},
                    )
                ) + "\n",
                encoding="utf-8",
            )
            (l3 / "routine_summaries.ndjson").write_text(
                "\n".join(
                    json.dumps(row)
                    for row in (
                        {"persona_id": "P0001", "routine": "자동저축", "frequency": "weekly", "ratio_pct_bps": 1200, "maintained_months": 3},
                        {"persona_id": "P0001", "routine": "카페 방문", "frequency": "weekly", "ratio_pct_bps": 900, "maintained_months": 2},
                        {"persona_id": "P0001", "routine": "투자 공부", "frequency": "weekly", "ratio_pct_bps": 500, "maintained_months": 1},
                    )
                ) + "\n",
                encoding="utf-8",
            )
            persona = {
                "personaId": "P0001", "cohort": "20s", "archetype": "프리랜서/크리에이터",
                "monthlyIncomeKrw": 2_000_000, "riskAttitude": "중립형", "incomeRegularity": "규칙적",
                "householdType": "월세", "lifestyleTags": ["카페선호"], "moneyWorry": "저축 걱정",
            }

            persona_projection = importer._runtime_persona_projection_seed(root, "v1.0.0", [persona])
            feature_projection = importer._runtime_feature_projection_seed(root, "v1.0.0")
            routine_projection = importer._runtime_routine_projection_seed(root, "v1.0.0")

            self.assertEqual(
                persona_projection.rows,
                (("P0001", "v1.0.0", "synthetic-runtime-v1", "AGE_24_29", "20s", "FREELANCER", "FROM_200_TO_300",
                  "BALANCED", "OVER_20", "BALANCED", "REGULAR", "RENT", '["카페선호"]', "SAVING", True, "[]", False),),
            )
            self.assertEqual(feature_projection.rows[0][2], "synthetic-runtime-v1")
            self.assertEqual(feature_projection.rows[0][3], "2026-07-01")
            self.assertEqual(
                [(row[3], row[4]) for row in routine_projection.rows],
                [("자동저축", "SAVING"), ("카페 방문", "SPENDING")],
            )
            self.assertIn("ON CONFLICT", persona_projection.sql)
            self.assertIn("ON CONFLICT", feature_projection.sql)
            self.assertIn("ON CONFLICT", routine_projection.sql)
            self.assertEqual(persona_projection.sql.count("%s"), len(persona_projection.rows[0]))
            self.assertEqual(feature_projection.sql.count("%s"), len(feature_projection.rows[0]))
            self.assertEqual(routine_projection.sql.count("%s"), len(routine_projection.rows[0]))

    def test_export_release_is_deterministic_and_emits_only_sanitized_l2(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            l3_source = root / "l3-source"
            bundle = source / "data" / "bundles" / "P0001"
            validation = source / "validation"
            bundle.mkdir(parents=True)
            validation.mkdir(parents=True)
            l3_dir = l3_source / "data" / "l3"
            l3_dir.mkdir(parents=True)
            policy = DatasetImportPolicy()
            all_l3_tables = policy.runtime_l3_tables | policy.golden_l3_tables | policy.excluded_l3_tables
            for table_name in all_l3_tables:
                (l3_dir / f"{table_name}.parquet").write_bytes(table_name.encode("utf-8"))
            l3_digest = importer.l3_tree_sha256(l3_source)
            (validation / "generation_manifest.json").write_text(
                json.dumps(
                    {
                        "seed": 20260713,
                        "demo_date": "2026-07-13",
                        "months": ["2026-01", "2026-07"],
                        "persona_count": 1,
                    }
                ),
                encoding="utf-8",
            )
            (bundle / "profile.json").write_text(
                json.dumps(
                    {
                        "persona_id": "P0001",
                        "synthetic_name": "가상청년 P0001",
                        "age": 21,
                        "archetype": "대학생/알바",
                        "cohort": "20s",
                        "job": "대학생",
                        "region": "대전 서구",
                        "monthly_income_krw": 1200000,
                        "income_regularity": "불규칙",
                        "target_saving_rate": 0.075,
                        "target_investment_rate": 0,
                        "risk_score": 2,
                        "risk_attitude": "안정추구형",
                        "household_type": "쉐어하우스",
                        "lifestyle_tags": ["카페선호"],
                        "financial_goal": "등록금 완충",
                        "money_worry": "과소비 걱정",
                        "joined_at": "2026-07-13",
                        "data_range": "2026-01~2026-07",
                        "demo_date": "2026-07-13",
                        "is_synthetic": True,
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (bundle / "manifest.json").write_text(
                json.dumps(
                    {
                        "persona_id": "P0001",
                        "generated_at": "2026-07-13T23:59:59",
                        "savings_product": "하나 여행 적금",
                        "housing_product": "주택청약종합저축",
                        "invest_participation": True,
                        "api_scope": ["금투-001"],
                        "files": {"금투-001": {"file_name": "secret.json"}},
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            invest_dir = bundle / "api" / "invest"
            invest_dir.mkdir(parents=True)
            (invest_dir / "금투-003-거래내역.json").write_text(
                json.dumps(
                    {
                        "trans_list": [
                            {
                                "prod_name": "KODEX 200",
                                "prod_code": "069500.KS",
                                "Bod": "매수",
                                "trans_num": 1,
                                "trans_amt": 50000,
                                "settle_amt": 50100,
                                "trans_dtime": "20260708111000",
                                "currency_code": "KRW",
                                "trans_no": "STK-SECRET",
                                "trans_memo": "raw memo",
                            }
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (invest_dir / "금투-004-보유상품.json").write_text(
                json.dumps(
                    {
                        "base_date": "20260713",
                        "prod_list": [
                            {
                                "prod_name": "KODEX 200",
                                "prod_code": "069500.KS",
                                "prod_type": "201",
                                "eval_amt": 1200000,
                                "purchase_amt": 1100000,
                                "holding_num": 8,
                                "currency_code": "KRW",
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            (bundle / "ledger.csv").write_text(
                "날짜,시간,타입,대분류,소분류,내용,금액,화폐,결제수단,메모,persona_id,transaction_id,cashflow_bucket,account_ref,api_ref,income_type,rule_id\n"
                "2026-07-13,16:08,지출,생활,편의점,CU 정림동점,-1800,KRW,카드,집 앞,P0001,P0001-T1,소비,acct,api.json#1,,calib:생활/편의점\n",
                encoding="utf-8-sig",
            )

            first = root / "first"
            second = root / "second"
            export_release(
                source,
                first,
                l3_source_root=l3_source,
                expected_l3_tree_sha256=l3_digest,
                l3_row_reader=lambda _path: [],
                verify_locked_export=False,
            )
            export_release(
                source,
                second,
                l3_source_root=l3_source,
                expected_l3_tree_sha256=l3_digest,
                l3_row_reader=lambda _path: [],
                verify_locked_export=False,
            )

            self.assertEqual((first / "personas.ndjson").read_bytes(), (second / "personas.ndjson").read_bytes())
            self.assertEqual((first / "financial_activities.ndjson").read_bytes(), (second / "financial_activities.ndjson").read_bytes())
            activity = json.loads((first / "financial_activities.ndjson").read_text(encoding="utf-8"))
            self.assertEqual(activity["amountKrw"], 1800)
            self.assertNotIn("정림동", str(activity))
            output_manifest = json.loads((first / "import-manifest.json").read_text(encoding="utf-8"))
            self.assertNotIn("sourceCommit", output_manifest)
            self.assertEqual(output_manifest["bundleSourceCommit"], importer.BUNDLE_SOURCE_COMMIT)
            self.assertEqual(output_manifest["l3SourceCommit"], importer.L3_SOURCE_COMMIT)
            self.assertEqual(output_manifest["l3TreeSha256"], l3_digest)
            self.assertEqual(
                output_manifest["counts"],
                {
                    "cosmeticCatalogItems": 3,
                    "financialActivities": 1,
                    "financialProducts": 2,
                    "investmentHoldings": 1,
                    "investmentTrades": 1,
                    "personas": 1,
                },
            )
            self.assertEqual(
                (first / "cosmetic_catalog.ndjson").read_bytes(),
                (second / "cosmetic_catalog.ndjson").read_bytes(),
            )
            all_exported = "".join(
                (first / name).read_text(encoding="utf-8")
                for name in (
                    "financial_products.ndjson",
                    "investment_holdings.ndjson",
                    "investment_trades.ndjson",
                )
            )
            self.assertIn("069500.KS", all_exported)
            self.assertNotIn("STK-SECRET", all_exported)
            self.assertNotIn("raw memo", all_exported)

            output_manifest["archiveVerified"] = True
            output_manifest["l3TreeSha256"] = importer.EXPECTED_L3_TREE_SHA256
            policy = DatasetImportPolicy()
            output_manifest["runtimeL3Counts"] = {name: 0 for name in sorted(policy.runtime_l3_tables)}
            output_manifest["goldenL3Counts"] = {name: 0 for name in sorted(policy.golden_l3_tables)}
            for directory, table_names in (
                (first / "l3", policy.runtime_l3_tables),
                (first / "golden_l3", policy.golden_l3_tables),
            ):
                directory.mkdir(exist_ok=True)
                for table_name in table_names:
                    (directory / f"{table_name}.ndjson").touch()
            (first / "import-manifest.json").write_text(
                json.dumps(output_manifest, ensure_ascii=False), encoding="utf-8"
            )

            persona_path = first / "personas.ndjson"
            original_persona = persona_path.read_bytes()
            persona_path.write_bytes(original_persona.replace(b"P0001", b"P9999", 1))
            with self.assertRaisesRegex(ValueError, "export file checksum"):
                build_seed_operations(first, allow_unverified=True)
            persona_path.write_bytes(original_persona)

            unexpected = first / "l3" / "unapproved.ndjson"
            unexpected.touch()
            with self.assertRaisesRegex(ValueError, "runtime L3 file set"):
                build_seed_operations(first, allow_unverified=True)
            unexpected.unlink()

            output_manifest["runtimeL3Counts"]["features"] = 1
            (first / "import-manifest.json").write_text(
                json.dumps(output_manifest, ensure_ascii=False), encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "runtime L3 row count"):
                build_seed_operations(first, allow_unverified=True)
            output_manifest["runtimeL3Counts"]["features"] = 0
            (first / "import-manifest.json").write_text(
                json.dumps(output_manifest, ensure_ascii=False), encoding="utf-8"
            )

            old_manifest = dict(output_manifest)
            old_manifest.pop("l3SourceCommit")
            old_manifest["sourceCommit"] = importer.BUNDLE_SOURCE_COMMIT
            (first / "import-manifest.json").write_text(
                json.dumps(old_manifest, ensure_ascii=False), encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "legacy sourceCommit"):
                build_seed_operations(first)
            (first / "import-manifest.json").write_text(
                json.dumps(output_manifest, ensure_ascii=False), encoding="utf-8"
            )

            operations = build_seed_operations(first, allow_unverified=True)
            self.assertEqual(
                [operation.name for operation in operations],
                [
                    "dataset_release",
                    "personas",
                    "financial_activities",
                    "financial_products",
                    "investment_holdings",
                    "investment_trades",
                    "cosmetic_catalog",
                    "runtime_l3_snapshot_prune",
                    "runtime_l3_records",
                    "runtime_persona_projection",
                    "runtime_feature_projection",
                    "runtime_routine_projection",
                ],
            )
            prune_index = next(
                index for index, operation in enumerate(operations)
                if operation.name == "runtime_l3_snapshot_prune"
            )
            insert_index = next(
                index for index, operation in enumerate(operations)
                if operation.name == "runtime_l3_records"
            )
            self.assertLess(prune_index, insert_index)
            self.assertIn("DELETE FROM finmate_import_l3_record", operations[prune_index].sql)
            projection_index = next(
                index for index, operation in enumerate(operations)
                if operation.name == "runtime_persona_projection"
            )
            self.assertLess(insert_index, projection_index)
            self.assertIn("finmate_synthetic_runtime_persona", operations[projection_index].sql)
            self.assertIn("ON CONFLICT", operations[projection_index].sql)
            self.assertTrue(
                all(
                    operation.name == "runtime_l3_snapshot_prune" or "ON CONFLICT" in operation.sql
                    for operation in operations
                )
            )


if __name__ == "__main__":
    unittest.main()
