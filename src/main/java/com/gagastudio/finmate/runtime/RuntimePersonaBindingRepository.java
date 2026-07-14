package com.gagastudio.finmate.runtime;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimePersonaBindingRepository {
	private final JdbcTemplate jdbcTemplate;

	RuntimePersonaBindingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<RuntimePersonaBinding> findByUserId(UUID userId) {
		return jdbcTemplate.query("""
			SELECT source_persona_id, release_version, projection_version
			FROM finmate_user_synthetic_persona_binding
			WHERE user_id = ?
			""", (resultSet, rowNumber) -> new RuntimePersonaBinding(
			resultSet.getString("source_persona_id"),
			resultSet.getString("release_version"),
			resultSet.getString("projection_version")), userId).stream().findFirst();
	}
}
