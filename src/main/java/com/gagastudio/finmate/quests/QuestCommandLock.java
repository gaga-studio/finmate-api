package com.gagastudio.finmate.quests;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class QuestCommandLock {
	private final JdbcTemplate jdbcTemplate;

	QuestCommandLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void lockUser(UUID userId) {
		jdbcTemplate.queryForObject("SELECT id FROM finmate_user WHERE id = ? FOR UPDATE", UUID.class, userId);
	}
}
