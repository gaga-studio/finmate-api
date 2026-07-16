package com.gagastudio.finmate.runtime;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeBehaviorRepository {
	private final JdbcTemplate jdbcTemplate;

	RuntimeBehaviorRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<RuntimeBehaviorProfile> findProfile(RuntimePersonaBinding binding) {
		return jdbcTemplate.query("""
			SELECT risk_profile_checked, diversification_checked, investment_learning_completed,
				investment_judgment_bps, quest_xp, last_evidence_date
			FROM finmate_synthetic_runtime_behavior_profile
			WHERE source_persona_id = ? AND release_version = ?
			""", (resultSet, rowNumber) -> new RuntimeBehaviorProfile(
			resultSet.getBoolean("risk_profile_checked"),
			resultSet.getBoolean("diversification_checked"),
			resultSet.getBoolean("investment_learning_completed"),
			resultSet.getInt("investment_judgment_bps"),
			resultSet.getInt("quest_xp"),
			resultSet.getObject("last_evidence_date", LocalDate.class)),
			binding.sourcePersonaId(), binding.releaseVersion()).stream().findFirst();
	}

	Integer budgetAdherenceBps(RuntimePersonaBinding binding, YearMonth month) {
		return jdbcTemplate.queryForObject("""
			WITH daily AS (
				SELECT activity_date, daily_budget_krw,
					GREATEST(0, cumulative_spend_krw - LAG(cumulative_spend_krw, 1, 0)
						OVER (ORDER BY activity_date)) AS daily_spent_krw
				FROM finmate_synthetic_runtime_daily_budget
				WHERE source_persona_id = ? AND release_version = ?
					AND activity_date >= ? AND activity_date < ?
			)
			SELECT CASE WHEN count(*) < 7 THEN NULL ELSE
				ROUND(10000.0 * count(*) FILTER (WHERE daily_spent_krw <= daily_budget_krw) / count(*))::integer END
			FROM daily
			""", Integer.class, binding.sourcePersonaId(), binding.releaseVersion(),
			Date.valueOf(month.atDay(1)), Date.valueOf(month.plusMonths(1).atDay(1)));
	}

	Optional<RuntimeBudgetStatus> budgetStatus(RuntimePersonaBinding binding, LocalDate date) {
		YearMonth month = YearMonth.from(date);
		return jdbcTemplate.query("""
			WITH daily AS (
				SELECT activity_date, daily_budget_krw,
					GREATEST(0, cumulative_spend_krw - LAG(cumulative_spend_krw, 1, 0)
						OVER (ORDER BY activity_date)) AS daily_spent_krw
				FROM finmate_synthetic_runtime_daily_budget
				WHERE source_persona_id = ? AND release_version = ?
					AND activity_date >= ? AND activity_date < ?
			)
			SELECT daily_budget_krw, daily_spent_krw
			FROM daily WHERE activity_date = ?
			""", (resultSet, rowNumber) -> {
			long budget = resultSet.getLong("daily_budget_krw");
			long spent = resultSet.getLong("daily_spent_krw");
			long remaining = Math.max(0, budget - spent);
			int usedBps = budget <= 0 ? 0 : (int) Math.min(10_000, Math.round(spent * 10_000.0 / budget));
			return new RuntimeBudgetStatus(budget, spent, remaining, usedBps);
		}, binding.sourcePersonaId(), binding.releaseVersion(), Date.valueOf(month.atDay(1)),
			Date.valueOf(month.plusMonths(1).atDay(1)), Date.valueOf(date)).stream().findFirst();
	}
}
