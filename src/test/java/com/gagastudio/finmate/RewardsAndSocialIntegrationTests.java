package com.gagastudio.finmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class RewardsAndSocialIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;

	@Test
	void behaviorQuestAwardsXpAndDeterministicCosmeticPointsExactlyOnce() throws Exception {
		String authorization = authorization(signUp("point-award@example.com"));
		String questId = questId(authorization, 0);

		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "point-quest-completion-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.xpAwarded").value(10))
			.andExpect(jsonPath("$.pointsAwarded").value(5))
			.andExpect(jsonPath("$.financialStatsChanged").value(false));
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "point-quest-completion-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pointsAwarded").value(5));

		mockMvc.perform(get("/api/v1/rewards/points").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balance").value(5))
			.andExpect(jsonPath("$.entries.length()").value(1))
			.andExpect(jsonPath("$.entries[0].sourceType").value("QUEST"));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM finmate_point_ledger WHERE source_id = ?",
			Long.class, questId)).isEqualTo(1L);
	}

	@Test
	void financialEvidenceQuestAwardsNothingUntilEvidenceIsVerified() throws Exception {
		String authorization = authorization(signUp("point-pending@example.com"));
		String questId = questId(authorization, 5);

		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "point-pending-completion-01"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.xpAwarded").value(0))
			.andExpect(jsonPath("$.pointsAwarded").value(0));
		mockMvc.perform(get("/api/v1/rewards/points").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balance").value(0))
			.andExpect(jsonPath("$.entries").isEmpty());
	}

	@Test
	void pointCatalogContainsOnlyFixedCosmeticsAndPurchaseIsIdempotent() throws Exception {
		String authorization = authorization(signUp("cosmetic-purchase@example.com"));
		String questId = questId(authorization, 0);
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
			.header("Idempotency-Key", "cosmetic-earn-points-0001")).andExpect(status().isOk());

		MvcResult catalog = mockMvc.perform(get("/api/v1/rewards/cosmetics").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].itemType").value("OUTFIT"))
			.andExpect(jsonPath("$.items[0].pricePoints").value(5))
			.andReturn();
		String body = catalog.getResponse().getContentAsString();
		for (String forbidden : new String[] {"coupon", "cash", "random", "box", "reportUnlock"}) {
			assertThat(body.toLowerCase()).doesNotContain(forbidden.toLowerCase());
		}

		mockMvc.perform(post("/api/v1/rewards/cosmetics/cosmetic-outfit-mint/purchase")
				.header("Authorization", authorization).header("Idempotency-Key", "cosmetic-purchase-key-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owned").value(true))
			.andExpect(jsonPath("$.balance").value(0));
		mockMvc.perform(post("/api/v1/rewards/cosmetics/cosmetic-outfit-mint/purchase")
				.header("Authorization", authorization).header("Idempotency-Key", "cosmetic-purchase-key-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balance").value(0));
	}

	@Test
	void syntheticFriendsFeedAndStreaksAreAmountFreeAndReadOnly() throws Exception {
		String authorization = authorization(signUp("social-readonly@example.com"));

		mockMvc.perform(get("/api/v1/mate/friends/overview").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.friendCount").value(5))
			.andExpect(jsonPath("$.completedToday").value(3))
			.andExpect(jsonPath("$.readOnly").value(true));
		MvcResult feed = mockMvc.perform(get("/api/v1/mate/friends/feed").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].completed").isBoolean())
			.andReturn();
		String feedBody = feed.getResponse().getContentAsString();
		for (String forbidden : new String[] {"amountKrw", "productName", "ticker", "trade", "account"}) {
			assertThat(feedBody).doesNotContain("\"" + forbidden + "\"");
		}
		mockMvc.perform(get("/api/v1/mate/friends/streaks").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].daysTogether").value(18))
			.andExpect(jsonPath("$.readOnly").value(true));

		mockMvc.perform(post("/api/v1/mate/friends/feed").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isMethodNotAllowed());
	}

	private String questId(String authorization, int index) throws Exception {
		return response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(index).path("questId").asText();
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
}
