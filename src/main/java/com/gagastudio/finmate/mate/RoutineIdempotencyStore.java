package com.gagastudio.finmate.mate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RoutineIdempotencyStore {
	private final JdbcTemplate jdbcTemplate;

	RoutineIdempotencyStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<StoredRoutineCommand> find(UUID userId, String operation, String idempotencyKey) {
		return jdbcTemplate.query("""
			SELECT user_id, operation, idempotency_key, request_fingerprint, original_status, original_body,
			       result_build_id, archived_build_id, active_build_id, created_at
			FROM finmate_routine_idempotency_command
			WHERE user_id = ? AND operation = ? AND idempotency_key = ?
			""", this::command, userId, operation, idempotencyKey).stream().findFirst();
	}

	boolean insert(StoredRoutineCommand command) {
		return jdbcTemplate.update("""
			INSERT INTO finmate_routine_idempotency_command
			    (user_id, operation, idempotency_key, request_fingerprint, original_status, original_body,
			     result_build_id, archived_build_id, active_build_id, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING
			""", command.userId(), command.operation(), command.idempotencyKey(), command.requestFingerprint(),
			command.originalStatus(), command.originalBody(), command.resultBuildId(), command.archivedBuildId(),
			command.activeBuildId(), Timestamp.from(command.createdAt())) == 1;
	}

	private StoredRoutineCommand command(ResultSet resultSet, int rowNumber) throws SQLException {
		return new StoredRoutineCommand(resultSet.getObject("user_id", UUID.class), resultSet.getString("operation"),
			resultSet.getString("idempotency_key"), resultSet.getString("request_fingerprint"),
			resultSet.getInt("original_status"), resultSet.getString("original_body"), uuid(resultSet, "result_build_id"),
			uuid(resultSet, "archived_build_id"), uuid(resultSet, "active_build_id"),
			resultSet.getTimestamp("created_at").toInstant());
	}

	private UUID uuid(ResultSet resultSet, String column) throws SQLException {
		return resultSet.getObject(column, UUID.class);
	}
}
