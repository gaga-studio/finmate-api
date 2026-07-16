package com.gagastudio.finmate.runtime;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("demo")
class DemoSyntheticRuntimeFixtureConfiguration {
	private static final String RELEASE_VERSION = "v1.0.0";
	private static final Instant SYNCED_AT = Instant.parse("2026-07-13T00:00:00Z");

	@Bean
	ApplicationRunner demoSyntheticRuntimeFixture(JdbcTemplate jdbc) {
		return new ApplicationRunner() {
			@Override
			public void run(ApplicationArguments arguments) {
				Integer runtimePersonaCount = jdbc.queryForObject(
					"SELECT COUNT(*) FROM finmate_synthetic_runtime_persona", Integer.class);
				if (runtimePersonaCount != null && runtimePersonaCount > 0) return;

				for (int index = 1; index <= 12; index++) {
					String sourcePersonaId = "DEMO-SEARCH-EXACT-%02d".formatted(index);
					seedPersona(jdbc, sourcePersonaId,
						"FROM_200_TO_300", "FROM_10_TO_20", "REGULAR", "RENT", 2 + index);
					seedFinancialActivities(jdbc, sourcePersonaId);
				}
				for (int index = 1; index <= 6; index++) {
					seedPersona(jdbc, "DEMO-SEARCH-RELAXED-%02d".formatted(index),
						"OVER_300", "UNDER_10", "IRREGULAR", "OTHER", 2 + index);
				}
			}
		};
	}

	private void seedPersona(JdbcTemplate jdbc, String sourcePersonaId, String incomeBand,
		String savingRateBand, String incomeRegularity, String householdType, int maintainedMonths) {
		jdbc.update("""
			INSERT INTO finmate_import_persona
				(source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
				 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
				 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
				 joined_at, source_data_range, source_data_as_of, synthetic)
			VALUES (?, ?, '25-29', '20s', 'demo_search', 'EARLY_CAREER', 2500000, ?, 1800, 1000,
				3, 'BALANCED', ?, '["자취","여행"]'::jsonb, 'SAVE', 'SAVING', DATE '2026-01-01',
				'2026-01~2026-07', DATE '2026-07-13', TRUE)
			ON CONFLICT (source_persona_id) DO NOTHING
			""", sourcePersonaId, RELEASE_VERSION, incomeRegularity, householdType);
		jdbc.update("""
			INSERT INTO finmate_synthetic_runtime_persona
				(source_persona_id, release_version, age_band, cohort, occupation_group, income_band,
				 spending_tendency, saving_rate_band, investment_tendency, income_regularity, household_type,
				 lifestyle_tags, money_worry, peer_discovery_opt_in, data_state, last_synced_at)
			VALUES (?, ?, 'AGE_24_29', '20s', 'EARLY_CAREER', ?, 'BALANCED', ?, 'BALANCED', ?, ?,
				'["자취","여행"]', 'SAVING', TRUE, 'FRESH', ?)
			ON CONFLICT (source_persona_id, release_version) DO NOTHING
			""", sourcePersonaId, RELEASE_VERSION, incomeBand, savingRateBand,
			incomeRegularity, householdType, Timestamp.from(SYNCED_AT));
		jdbc.update("""
			INSERT INTO finmate_synthetic_runtime_feature_profile
				(source_persona_id, release_version, feature_month, age, cohort, income_norm_bps,
				 essential_ratio_bps, consumption_rate_bps, saving_rate_bps, invest_rate_bps,
				 defense_score_bps, saving_score_bps, invest_score_bps, lifestyle_cluster_id)
			VALUES (?, ?, DATE '2026-07-01', 26, '20s', 5000, 4300, 4100, 1800, 900,
				7200, 6800, 6100, '11')
			ON CONFLICT (source_persona_id, release_version) DO NOTHING
			""", sourcePersonaId, RELEASE_VERSION);
		jdbc.update("""
			INSERT INTO finmate_synthetic_runtime_routine
				(source_persona_id, release_version, source_routine, domain, frequency, ratio_bps, maintained_months)
			VALUES (?, ?, 'automatic_saving', 'SAVING', 'MONTHLY', 1800, ?)
			ON CONFLICT (source_persona_id, release_version, source_routine) DO NOTHING
			""", sourcePersonaId, RELEASE_VERSION, maintainedMonths);
	}

	private void seedFinancialActivities(JdbcTemplate jdbc, String sourcePersonaId) {
		seedFinancialActivity(jdbc, sourcePersonaId + "-income-may", sourcePersonaId,
			"INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_800_000, "2026-05-09T00:00:00Z");
		seedFinancialActivity(jdbc, sourcePersonaId + "-income-june", sourcePersonaId,
			"INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_800_000, "2026-06-09T00:00:00Z");
		seedFinancialActivity(jdbc, sourcePersonaId + "-income-july", sourcePersonaId,
			"INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_800_000, "2026-07-09T00:00:00Z");
		seedFinancialActivity(jdbc, sourcePersonaId + "-essential-july", sourcePersonaId,
			"SPENDING", "OUTFLOW", "ESSENTIAL_EXPENSE", "주거비", 900_000, "2026-07-10T00:00:00Z");
		seedFinancialActivity(jdbc, sourcePersonaId + "-spending-july", sourcePersonaId,
			"SPENDING", "OUTFLOW", "DISCRETIONARY_EXPENSE", "생활비", 420_000, "2026-07-11T00:00:00Z");
		seedFinancialActivity(jdbc, sourcePersonaId + "-saving-july", sourcePersonaId,
			"SAVING", "OUTFLOW", "SAVING_CONTRIBUTION", "여행 저축", 500_000, "2026-07-12T00:00:00Z");
	}

	private void seedFinancialActivity(JdbcTemplate jdbc, String transactionId, String sourcePersonaId,
		String activityType, String direction, String classification, String label, long amountKrw,
		String occurredAt) {
		jdbc.update("""
			INSERT INTO finmate_financial_activity
				(source_transaction_id, source_persona_id, release_version, activity_type, direction,
				 classification, category, subcategory, display_label, amount_krw, currency, occurred_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'KRW', CAST(? AS TIMESTAMPTZ))
			ON CONFLICT (source_transaction_id) DO NOTHING
			""", transactionId, sourcePersonaId, RELEASE_VERSION, activityType, direction,
			classification, label, label, label, amountKrw, occurredAt);
	}
}
