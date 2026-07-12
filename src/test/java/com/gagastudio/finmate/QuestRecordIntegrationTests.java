package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class QuestRecordIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void listsSixRepresentativeQuestsWithoutChangingFinancialStats() throws Exception {
		String authorization = authorization(signUp("quest-list@example.com"));
		completeOnboarding(authorization);

		mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(6))
			.andExpect(jsonPath("$.items[0].financialStatsChanged").value(false))
			.andExpect(jsonPath("$.items[0].verificationKind").value("BEHAVIOR"))
			.andExpect(jsonPath("$.items[5].verificationKind").value("SYNTHETIC_MYDATA"))
			.andExpect(jsonPath("$.totalXp").value(0));
	}

	@Test
	void completesBehaviorQuestIdempotentlyWithXpOnly() throws Exception {
		String authorization = authorization(signUp("quest-complete@example.com"));
		completeOnboarding(authorization);
		String questId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(0).path("questId").asText();

		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "quest-completion-key-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quest.status").value("COMPLETED"))
			.andExpect(jsonPath("$.xpAwarded").value(10))
			.andExpect(jsonPath("$.financialStatsChanged").value(false));
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "quest-completion-key-0001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.xpAwarded").value(10));
		mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalXp").value(10));
	}

	@Test
	void leavesSyntheticEvidenceQuestDataPendingUntilASyntheticBatchArrives() throws Exception {
		String authorization = authorization(signUp("quest-pending@example.com"));
		completeOnboarding(authorization);
		String questId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(5).path("questId").asText();

		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "quest-pending-key-000001"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.quest.status").value("DATA_PENDING"))
			.andExpect(jsonPath("$.xpAwarded").value(0))
			.andExpect(jsonPath("$.financialStatsChanged").value(false));
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
				.header("Idempotency-Key", "quest-pending-key-000001"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.quest.status").value("DATA_PENDING"))
			.andExpect(jsonPath("$.xpAwarded").value(0));
	}

	@Test
	void concurrentSameKeyQuestCompletionReplaysOneCompletion() throws Exception {
		String authorization = authorization(signUp("quest-concurrent-same@example.com"));
		String questId = questId(authorization, 0);
		List<MvcResult> results = concurrently(
			() -> completeQuest(authorization, questId, "quest-concurrent-same-key"),
			() -> completeQuest(authorization, questId, "quest-concurrent-same-key"));

		org.assertj.core.api.Assertions.assertThat(results).extracting(result -> result.getResponse().getStatus())
			.containsExactlyInAnyOrder(200, 200);
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM finmate_quest_completion WHERE idempotency_key = ?", Long.class,
			"quest-concurrent-same-key")).isEqualTo(1L);
	}

	@Test
	void concurrentDifferentQuestCompletionKeysReturnOneConflict() throws Exception {
		String authorization = authorization(signUp("quest-concurrent-different@example.com"));
		String questId = questId(authorization, 0);
		List<MvcResult> results = concurrently(
			() -> completeQuest(authorization, questId, "quest-concurrent-first-key"),
			() -> completeQuest(authorization, questId, "quest-concurrent-other-key"));

		org.assertj.core.api.Assertions.assertThat(results).extracting(result -> result.getResponse().getStatus())
			.containsExactlyInAnyOrder(200, 409);
		MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
		org.assertj.core.api.Assertions.assertThat(response(conflict).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
	}

	@Test
	void recordsAreUserScopedAndReflectionsDoNotChangeGoalCalculations() throws Exception {
		String firstAuthorization = authorization(signUp("record-first@example.com"));
		String secondAuthorization = authorization(signUp("record-second@example.com"));
		completeOnboarding(firstAuthorization);
		completeOnboarding(secondAuthorization);
		String questId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", firstAuthorization)).andReturn())
			.path("items").get(0).path("questId").asText();
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", firstAuthorization)
				.header("Idempotency-Key", "record-quest-complete-001"))
			.andExpect(status().isOk());
		String date = java.time.LocalDate.now().toString();
		JsonNode homeBefore = response(mockMvc.perform(get("/api/v1/home").header("Authorization", firstAuthorization)).andReturn());

		mockMvc.perform(get("/api/v1/records").header("Authorization", firstAuthorization).queryParam("from", date).queryParam("to", date))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].events[0].eventType").value("QUEST"));
		mockMvc.perform(put("/api/v1/records/{date}", date).header("Authorization", firstAuthorization)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reflection\":\"Keep the travel goal visible.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reflection").value("Keep the travel goal visible."));
		org.assertj.core.api.Assertions.assertThat(response(mockMvc.perform(get("/api/v1/home").header("Authorization", firstAuthorization)).andReturn()))
			.isEqualTo(homeBefore);
		mockMvc.perform(get("/api/v1/records/{date}", date).header("Authorization", secondAuthorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events").isEmpty())
			.andExpect(jsonPath("$.reflection").isEmpty());
	}

	@Test
	void rejectsRecordRangesLongerThanThirtyOneInclusiveDays() throws Exception {
		String authorization = authorization(signUp("record-range@example.com"));
		mockMvc.perform(get("/api/v1/records").header("Authorization", authorization)
				.queryParam("from", "2026-01-01").queryParam("to", "2026-02-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void returnsQuestDetailOnlyToItsOwner() throws Exception {
		String firstAuthorization = authorization(signUp("quest-detail-first@example.com"));
		String secondAuthorization = authorization(signUp("quest-detail-second@example.com"));
		String questId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", firstAuthorization)).andReturn())
			.path("items").get(0).path("questId").asText();

		mockMvc.perform(get("/api/v1/quests/{questId}", questId).header("Authorization", firstAuthorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questId").value(questId));
		mockMvc.perform(get("/api/v1/quests/{questId}", questId).header("Authorization", secondAuthorization))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void nonDemoProfileDoesNotRegisterTheTimelineRoute() throws Exception {
		String authorization = authorization(signUp("non-demo-timeline@example.com"));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "non-demo-timeline-key01").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedStage\":0}"))
			.andExpect(status().isNotFound());
	}

	private void completeOnboarding(String authorization) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding").header("Authorization", authorization)
				.header("Idempotency-Key", "quest-onboarding-key-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"Mina\",\"mainGoal\":{\"title\":\"Europe travel fund\",\"domain\":\"SAVING\",\"currentAmountKrw\":2000000,\"targetAmountKrw\":5000000,\"targetMonth\":\"2027-01\"},\"confirmMainGoal\":true}"))
			.andExpect(status().isOk());
	}

	private String questId(String authorization, int index) throws Exception {
		return response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(index).path("questId").asText();
	}

	private MvcResult completeQuest(String authorization, String questId, String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId).header("Authorization", authorization)
			.header("Idempotency-Key", idempotencyKey)).andReturn();
	}

	private List<MvcResult> concurrently(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> firstResult = executor.submit(afterBarrier(barrier, first));
			Future<MvcResult> secondResult = executor.submit(afterBarrier(barrier, second));
			return List.of(firstResult.get(), secondResult.get());
		} finally {
			executor.shutdownNow();
		}
	}

	private Callable<MvcResult> afterBarrier(CyclicBarrier barrier, Callable<MvcResult> request) {
		return () -> { barrier.await(); return request.call(); };
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
