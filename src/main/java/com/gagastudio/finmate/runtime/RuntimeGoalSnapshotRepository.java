package com.gagastudio.finmate.runtime;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeGoalSnapshotRepository {
	private final JdbcTemplate jdbcTemplate;

	RuntimeGoalSnapshotRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<RuntimeGoalSnapshot> findLatestMonths(UUID userId, UUID goalId, int limit) {
		return jdbcTemplate.query("""
			SELECT snapshot_month, spending_bps, saving_bps, investment_judgment_bps, last_synced_at
			FROM (
				SELECT DISTINCT ON (snapshot_month) snapshot_month, spending_bps, saving_bps,
					investment_judgment_bps, last_synced_at
				FROM finmate_synthetic_financial_snapshot
				WHERE user_id = ? AND goal_id = ?
				ORDER BY snapshot_month DESC, last_synced_at DESC
			) monthly
			ORDER BY snapshot_month DESC
			LIMIT ?
			""", (resultSet, rowNumber) -> new RuntimeGoalSnapshot(
			java.time.YearMonth.from(resultSet.getDate("snapshot_month").toLocalDate()),
			resultSet.getInt("spending_bps"),
			resultSet.getInt("saving_bps"),
			resultSet.getInt("investment_judgment_bps"),
			resultSet.getTimestamp("last_synced_at").toInstant()), userId, goalId, limit);
	}
}
