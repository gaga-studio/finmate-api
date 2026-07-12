package com.gagastudio.finmate.goals;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OnboardingCommandLock {
	private final JdbcTemplate jdbcTemplate;

	OnboardingCommandLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void lockUser(UUID userId) {
		jdbcTemplate.queryForObject("SELECT id FROM finmate_user WHERE id = ? FOR UPDATE",
			(resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), userId);
	}
}
