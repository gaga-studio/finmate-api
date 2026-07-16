package com.gagastudio.finmate.runtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeFinancialActivityRepository {
	private final JdbcTemplate jdbcTemplate;

	RuntimeFinancialActivityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<Instant> findLatestOccurredAt(RuntimePersonaBinding binding) {
		return Optional.ofNullable(jdbcTemplate.queryForObject("""
			SELECT max(occurred_at)
			FROM finmate_financial_activity
			WHERE source_persona_id = ? AND release_version = ?
			""", Timestamp.class, binding.sourcePersonaId(), binding.releaseVersion())).map(Timestamp::toInstant);
	}

	List<RuntimeFinancialActivity> findBetween(RuntimePersonaBinding binding, Instant fromInclusive,
		Instant toExclusive) {
		return jdbcTemplate.query("""
			SELECT source_transaction_id, activity_type, direction, classification, category, subcategory,
				display_label, amount_krw, occurred_at
			FROM finmate_financial_activity
			WHERE source_persona_id = ? AND release_version = ?
				AND occurred_at >= ? AND occurred_at < ?
			ORDER BY occurred_at, source_transaction_id
			""", (resultSet, rowNumber) -> new RuntimeFinancialActivity(
			resultSet.getString("source_transaction_id"),
			resultSet.getString("activity_type"),
			resultSet.getString("direction"),
			resultSet.getString("classification"),
			resultSet.getString("category"),
			resultSet.getString("subcategory"),
			resultSet.getString("display_label"),
			resultSet.getLong("amount_krw"),
			resultSet.getTimestamp("occurred_at").toInstant()),
			binding.sourcePersonaId(), binding.releaseVersion(), Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
	}

	Optional<RuntimeSavingDelta> savingDeltaAfter(RuntimePersonaBinding binding, Instant afterExclusive) {
		return jdbcTemplate.query("""
			SELECT COALESCE(SUM(CASE WHEN direction = 'OUTFLOW' THEN amount_krw ELSE -amount_krw END), 0) AS net_inflow,
				max(occurred_at) AS last_synced_at
			FROM finmate_financial_activity
			WHERE source_persona_id = ? AND release_version = ?
				AND classification = 'SAVING_CONTRIBUTION' AND occurred_at > ?
			""", (resultSet, rowNumber) -> {
			java.sql.Timestamp lastSyncedAt = resultSet.getTimestamp("last_synced_at");
			return lastSyncedAt == null ? null : new RuntimeSavingDelta(
				resultSet.getLong("net_inflow"), lastSyncedAt.toInstant());
		}, binding.sourcePersonaId(), binding.releaseVersion(), Timestamp.from(afterExclusive)).stream()
			.filter(java.util.Objects::nonNull).findFirst();
	}
}
