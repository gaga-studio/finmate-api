package com.gagastudio.finmate.mate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SocialRuntimeRepository {
	private final JdbcTemplate jdbcTemplate;

	SocialRuntimeRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<SocialRuntimeScope> scope(UUID userId) {
		return jdbcTemplate.query("""
			SELECT source_persona_id, release_version, projection_version
			FROM finmate_user_synthetic_persona_binding
			WHERE user_id = ?
			""", (resultSet, rowNumber) -> new SocialRuntimeScope(
			resultSet.getString("source_persona_id"),
			resultSet.getString("release_version"),
			resultSet.getString("projection_version")), userId).stream().findFirst();
	}

	List<SocialRuntimeFriend> friends(SocialRuntimeScope scope) {
		return jdbcTemplate.query("""
			SELECT friend_public_id, friend_alias, avatar_code, quest_completed_today
			FROM finmate_synthetic_social_friend
			WHERE viewer_persona_id = ? AND release_version = ?
			ORDER BY connected_at, friend_public_id
			""", (resultSet, rowNumber) -> new SocialRuntimeFriend(
			resultSet.getString("friend_public_id"),
			resultSet.getString("friend_alias"),
			resultSet.getString("avatar_code"),
			resultSet.getBoolean("quest_completed_today")),
			scope.sourcePersonaId(), scope.releaseVersion());
	}

	List<SocialRuntimeFeedEvent> feed(SocialRuntimeScope scope) {
		return jdbcTemplate.query("""
			SELECT friend.friend_public_id, friend.friend_alias, friend.avatar_code,
			       event.event_type, event.event_date
			FROM finmate_synthetic_social_feed_event event
			JOIN finmate_synthetic_social_friend friend
			  ON friend.viewer_persona_id = event.viewer_persona_id
			 AND friend.friend_persona_id = event.subject_persona_id
			 AND friend.release_version = event.release_version
			WHERE event.viewer_persona_id = ? AND event.release_version = ?
			ORDER BY event.event_date DESC, event.event_id
			""", (resultSet, rowNumber) -> new SocialRuntimeFeedEvent(
			resultSet.getString("friend_public_id"),
			resultSet.getString("friend_alias"),
			resultSet.getString("avatar_code"),
			resultSet.getString("event_type"),
			resultSet.getObject("event_date", LocalDate.class)),
			scope.sourcePersonaId(), scope.releaseVersion());
	}

	List<SocialRuntimeStreak> streaks(SocialRuntimeScope scope) {
		return jdbcTemplate.query("""
			SELECT friend.friend_public_id, friend.friend_alias, streak.current_streak
			FROM finmate_synthetic_social_streak streak
			JOIN finmate_synthetic_social_friend friend
			  ON friend.viewer_persona_id = streak.viewer_persona_id
			 AND friend.friend_persona_id = streak.friend_persona_id
			 AND friend.release_version = streak.release_version
			WHERE streak.viewer_persona_id = ? AND streak.release_version = ?
			ORDER BY streak.current_streak DESC, friend.friend_public_id
			""", (resultSet, rowNumber) -> new SocialRuntimeStreak(
			resultSet.getString("friend_public_id"),
			resultSet.getString("friend_alias"),
			resultSet.getInt("current_streak")),
			scope.sourcePersonaId(), scope.releaseVersion());
	}
}

record SocialRuntimeScope(String sourcePersonaId, String releaseVersion, String projectionVersion) {
}

record SocialRuntimeFriend(String publicId, String alias, String avatarCode, boolean questCompletedToday) {
}

record SocialRuntimeFeedEvent(String publicId, String alias, String avatarCode, String eventType,
	LocalDate eventDate) {
}

record SocialRuntimeStreak(String publicId, String alias, int currentStreak) {
}
