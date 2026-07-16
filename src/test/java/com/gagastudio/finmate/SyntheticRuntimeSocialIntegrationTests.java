package com.gagastudio.finmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SyntheticRuntimeSocialIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;

	private String authorization;

	@BeforeEach
	void setUpBoundRuntimeSocialGraph() throws Exception {
		String email = "runtime-social-%s@example.com".formatted(UUID.randomUUID());
		authorization = authorization(signUp(email));
		UUID userId = jdbcTemplate.queryForObject(
			"SELECT id FROM finmate_user WHERE email = ?", UUID.class, email);

		for (String personaId : new String[] {"SOC_VIEWER", "SOC_FRIEND_A", "SOC_FRIEND_B", "SOC_OTHER"}) {
			insertRuntimePersona(personaId);
		}
		jdbcTemplate.update("""
			INSERT INTO finmate_user_synthetic_persona_binding
				(user_id, source_persona_id, release_version, projection_version, bound_at)
			VALUES (?, 'SOC_VIEWER', 'v1.0.0', 'synthetic-runtime-v1', CURRENT_TIMESTAMP)
			ON CONFLICT (user_id) DO UPDATE SET source_persona_id = EXCLUDED.source_persona_id
			""", userId);

		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_social_friend
				(viewer_persona_id, friend_persona_id, release_version, projection_version,
				 friend_public_id, friend_alias, avatar_code, quest_completed_today, connected_at)
			VALUES
				('SOC_VIEWER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1',
				 'friend-a-safe', '민트 나침반', 'MATE_BEAR', TRUE, '2026-02-13'),
				('SOC_VIEWER', 'SOC_FRIEND_B', 'v1.0.0', 'synthetic-runtime-v1',
				 'friend-b-safe', '푸른 등불', 'MATE_RABBIT', FALSE, '2026-03-01'),
				('SOC_OTHER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1',
				 'other-friend-safe', '다른 사용자 친구', 'MATE_BIRD', TRUE, '2026-03-02')
			ON CONFLICT DO NOTHING
			""");
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_social_feed_event
				(event_id, viewer_persona_id, subject_persona_id, release_version, projection_version,
				 event_type, stat_delta_bps, event_date)
			VALUES
				('event-viewer-a', 'SOC_VIEWER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1',
				 'goal_stage_clear', NULL, '2026-07-12'),
				('event-other-a', 'SOC_OTHER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1',
				 'stat_up:defense_score', 420, '2026-07-13')
			ON CONFLICT DO NOTHING
			""");
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_social_streak
				(viewer_persona_id, friend_persona_id, release_version, projection_version,
				 current_streak, best_streak, unit)
			VALUES
				('SOC_VIEWER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1', 18, 24, '일'),
				('SOC_OTHER', 'SOC_FRIEND_A', 'v1.0.0', 'synthetic-runtime-v1', 99, 99, '일')
			ON CONFLICT DO NOTHING
			""");
	}

	@Test
	void friendOverviewIsScopedToTheAuthenticatedUsersPermanentPersona() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/mate/friends/overview")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.friendCount").value(2))
			.andExpect(jsonPath("$.completedToday").value(1))
			.andExpect(jsonPath("$.readOnly").value(true))
			.andExpect(jsonPath("$.friends.length()").value(2))
			.andExpect(jsonPath("$.friends[0].friendId").value("friend-a-safe"))
			.andReturn();

		assertNoSourcePersonaId(result);
	}

	@Test
	void feedReconstructsSafeCopyAndDoesNotLeakAnotherViewersEvents() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/mate/friends/feed")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.readOnly").value(true))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].friendId").value("friend-a-safe"))
			.andExpect(jsonPath("$.items[0].alias").value("민트 나침반"))
			.andExpect(jsonPath("$.items[0].message").value("민트 나침반님이 목표 레이드 단계를 완료했어요"))
			.andReturn();

		assertNoSourcePersonaId(result);
		assertThat(result.getResponse().getContentAsString()).doesNotContain("다른 사용자 친구");
	}

	@Test
	void friendStreaksUseOnlyPairDailyProjectionForTheBoundViewer() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/mate/friends/streaks")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.readOnly").value(true))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].friendId").value("friend-a-safe"))
			.andExpect(jsonPath("$.items[0].daysTogether").value(18))
			.andReturn();

		assertNoSourcePersonaId(result);
	}

	private void insertRuntimePersona(String personaId) {
		jdbcTemplate.update("""
			INSERT INTO finmate_import_persona
				(source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
				 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
				 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
				 joined_at, source_data_range, source_data_as_of, synthetic)
			VALUES (?, 'v1.0.0', '24-29', 'early-career', '사회초년생', 'EARLY_CAREER',
				2400000, 'REGULAR', 2000, 1000, 2, '중립형', 'RENT', '[]'::jsonb,
				'비상금', '저축', '2026-01-01', '2026-01~2026-07', '2026-07-13', TRUE)
			ON CONFLICT (source_persona_id) DO NOTHING
			""", personaId);
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_persona
				(source_persona_id, release_version, projection_version, age_band, cohort, occupation_group,
				 income_band, spending_tendency, saving_rate_band, investment_tendency, income_regularity,
				 household_type, lifestyle_tags, money_worry, peer_discovery_opt_in, data_state,
				 last_synced_at, visible_fields, exact_values)
			VALUES (?, 'v1.0.0', 'synthetic-runtime-v1', 'AGE_24_29', 'early-career', 'EARLY_CAREER',
				'FROM_200_TO_300', 'BALANCED', 'OVER_20', 'BALANCED', 'REGULAR', 'RENT', '[]',
				'SAVING', TRUE, 'FRESH', '2026-07-13', '[]', FALSE)
			ON CONFLICT (source_persona_id, release_version) DO NOTHING
			""", personaId);
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}".formatted(email)))
			.andExpect(status().isCreated()).andReturn();
	}

	private String authorization(MvcResult signup) throws Exception {
		return "Bearer " + response(signup).path("accessToken").asText();
	}

	private JsonNode response(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void assertNoSourcePersonaId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		for (String sourceId : new String[] {"SOC_VIEWER", "SOC_FRIEND_A", "SOC_FRIEND_B", "SOC_OTHER"}) {
			assertThat(body).doesNotContain(sourceId);
		}
	}
}
