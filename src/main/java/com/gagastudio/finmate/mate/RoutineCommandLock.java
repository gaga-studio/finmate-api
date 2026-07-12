package com.gagastudio.finmate.mate;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class RoutineCommandLock {
	private final JdbcTemplate jdbcTemplate;

	RoutineCommandLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void lock(UUID userId) {
		jdbcTemplate.queryForObject("SELECT id FROM finmate_user WHERE id = ? FOR UPDATE", UUID.class, userId);
	}
}
