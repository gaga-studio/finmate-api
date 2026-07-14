package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class GoalCommandStore {
	private final JdbcTemplate jdbcTemplate;

	GoalCommandStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<StoredGoalCommand> find(UUID userId, String idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT request_fingerprint, goal_id
			FROM finmate_goal_command
			WHERE user_id = ? AND idempotency_key = ?
			""", (resultSet, rowNumber) -> new StoredGoalCommand(
			resultSet.getString("request_fingerprint"), resultSet.getObject("goal_id", UUID.class)),
			userId, idempotencyKey).stream().findFirst();
	}

	void save(UUID userId, String idempotencyKey, String fingerprint, UUID goalId, Instant createdAt) {
		jdbcTemplate.update("""
			INSERT INTO finmate_goal_command
			(user_id, idempotency_key, request_fingerprint, goal_id, created_at)
			VALUES (?, ?, ?, ?, ?)
			""", userId, idempotencyKey, fingerprint, goalId, java.sql.Timestamp.from(createdAt));
	}

	record StoredGoalCommand(String fingerprint, UUID goalId) {
	}
}
