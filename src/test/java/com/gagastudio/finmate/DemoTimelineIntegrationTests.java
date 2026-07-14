package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.goals.SyntheticSnapshotIngestionService;
import com.gagastudio.finmate.goals.SyntheticSnapshotInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Testcontainers
class DemoTimelineIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired SyntheticSnapshotIngestionService snapshotIngestion;

	@Test
	void demoTimelineAdvancesAtomicallyReplaysAndCulminatesAtGoalCompletion() throws Exception {
		String authorization = authorization(signUp("demo-timeline@example.com"));
		completeOnboarding(authorization);
		String pendingQuestId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(5).path("questId").asText();
		mockMvc.perform(post("/api/v1/quests/{questId}/accept", pendingQuestId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "demo-pending-accept-key1"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", pendingQuestId).header("Authorization", authorization)
				.header("Idempotency-Key", "demo-pending-quest-key01"))
			.andExpect(status().isAccepted());

		advance(authorization, 0, "demo-timeline-frame-one")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentFrameIndex").value(0))
			.andExpect(jsonPath("$.frames.length()").value(6))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(2_500_000));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-frame-one").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedFrameIndex\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentFrameIndex").value(0));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-stale-key").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedFrameIndex\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DATA_STALE"));
		mockMvc.perform(get("/api/v1/quests/{questId}", pendingQuestId).header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", pendingQuestId).header("Authorization", authorization)
				.header("Idempotency-Key", "demo-pending-quest-key01"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quest.status").value("COMPLETED"))
			.andExpect(jsonPath("$.xpAwarded").value(30));

		advance(authorization, 1, "demo-timeline-frame-two");
		advance(authorization, 2, "demo-timeline-frame-three");
		advance(authorization, 3, "demo-timeline-frame-four");
		advance(authorization, 4, "demo-timeline-frame-five");
		advance(authorization, 5, "demo-timeline-frame-six")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentFrameIndex").value(5))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(5_000_000))
			.andExpect(jsonPath("$.mainGoal.state").value("COMPLETED"))
			.andExpect(jsonPath("$.raid.currentProgressBps").value(10_000))
			.andExpect(jsonPath("$.raid.bossHpBps").value(0))
			.andExpect(jsonPath("$.dataState").value("FRESH"));
		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mainGoal.state").value("COMPLETED"))
			.andExpect(jsonPath("$.raid.status").value("COMPLETED"));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-frame-one").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedFrameIndex\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentFrameIndex").value(0))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(2_500_000))
			.andExpect(jsonPath("$.raid.currentProgressBps").value(1_666))
			.andExpect(jsonPath("$.raid.coachCopyKey").value("RAID_STAGE_1_WAITING_V2"));
		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-08"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recordedDayCount").value(1))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(500_000))
			.andExpect(jsonPath("$.nodes[9].primaryActivity.activityType").value("SAVING"));
		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2027-01"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recordedDayCount").value(1))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(500_000));
	}

	@Test
	void rejectsAnOmittedExpectedFrameIndex() throws Exception {
		String authorization = authorization(signUp("demo-missing-stage@example.com"));
		completeOnboarding(authorization);

		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-missing-stage-key01").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void rejectsSameDemoKeyUsedForADifferentRequest() throws Exception {
		String authorization = authorization(signUp("demo-key-conflict@example.com"));
		completeOnboarding(authorization);
		advance(authorization, 0, "demo-key-conflict-key01").andExpect(status().isOk());

		advance(authorization, 1, "demo-key-conflict-key01")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void concurrentFirstAndSubsequentDemoRequestsReplayOneCommand() throws Exception {
		String authorization = authorization(signUp("demo-concurrent@example.com"));
		completeOnboarding(authorization);
		List<MvcResult> first = concurrently(
			() -> advanceResult(authorization, 0, "demo-concurrent-first-key"),
			() -> advanceResult(authorization, 0, "demo-concurrent-first-key"));
		org.assertj.core.api.Assertions.assertThat(first).extracting(result -> result.getResponse().getStatus())
			.containsExactlyInAnyOrder(200, 200);
		for (MvcResult result : first) {
			org.assertj.core.api.Assertions.assertThat(response(result).path("currentFrameIndex").asInt()).isEqualTo(0);
		}

		List<MvcResult> subsequent = concurrently(
			() -> advanceResult(authorization, 1, "demo-concurrent-next-key1"),
			() -> advanceResult(authorization, 1, "demo-concurrent-next-key1"));
		org.assertj.core.api.Assertions.assertThat(subsequent).extracting(result -> result.getResponse().getStatus())
			.containsExactlyInAnyOrder(200, 200);
		for (MvcResult result : subsequent) {
			org.assertj.core.api.Assertions.assertThat(response(result).path("currentFrameIndex").asInt()).isEqualTo(1);
		}
	}

	@Test
	void demoReplayPreservesTheFullOriginalResponseAfterLaterSharedIngestion() throws Exception {
		MvcResult signup = signUp("demo-immutable-replay@example.com");
		String authorization = authorization(signup);
		completeOnboarding(authorization);
		JsonNode original = response(advance(authorization, 0, "demo-immutable-replay-key").andReturn());

		snapshotIngestion.ingest(userId(signup), new SyntheticSnapshotInput(4_100_000, 4_100, 4_200, 4_300, 777,
			Instant.now().plusSeconds(1)));

		JsonNode replay = response(advance(authorization, 0, "demo-immutable-replay-key").andReturn());
		org.assertj.core.api.Assertions.assertThat(replay).isEqualTo(original);
	}

	private ResultActions advance(String authorization, int expectedFrameIndex, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedFrameIndex\":%d}".formatted(expectedFrameIndex)));
	}

	private MvcResult advanceResult(String authorization, int expectedFrameIndex, String key) throws Exception {
		return advance(authorization, expectedFrameIndex, key).andReturn();
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

	private void completeOnboarding(String authorization) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-onboarding-key-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"Mina\",\"mainGoal\":{\"title\":\"Europe travel fund\",\"domain\":\"SAVING\",\"currentAmountKrw\":2000000,\"targetAmountKrw\":5000000,\"targetMonth\":\"2027-01\"},\"confirmMainGoal\":true}"))
			.andExpect(status().isOk());
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}".formatted(email)))
			.andExpect(status().isCreated()).andReturn();
	}

	private String authorization(MvcResult signup) throws Exception { return "Bearer " + response(signup).path("accessToken").asText(); }
	private UUID userId(MvcResult signup) throws Exception { return UUID.fromString(response(signup).path("user").path("userId").asText()); }
	private JsonNode response(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
}
