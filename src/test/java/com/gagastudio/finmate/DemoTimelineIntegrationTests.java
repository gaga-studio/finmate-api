package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

	@Test
	void demoTimelineAdvancesAtomicallyReplaysAndCulminatesAtGoalCompletion() throws Exception {
		String authorization = authorization(signUp("demo-timeline@example.com"));
		completeOnboarding(authorization);
		String pendingQuestId = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn())
			.path("items").get(5).path("questId").asText();
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", pendingQuestId).header("Authorization", authorization)
				.header("Idempotency-Key", "demo-pending-quest-key01"))
			.andExpect(status().isAccepted());

		advance(authorization, 0, "demo-timeline-stage-one")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(1));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-stage-one").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedStage\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(1));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-stale-key").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedStage\":0}"))
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

		advance(authorization, 1, "demo-timeline-stage-two");
		advance(authorization, 2, "demo-timeline-stage-three")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(3))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(5_000_000))
			.andExpect(jsonPath("$.raid.progressBps").value(10_000))
			.andExpect(jsonPath("$.raid.bossHpBps").value(0))
			.andExpect(jsonPath("$.syntheticGroup.groupId").value("group-demo-10"));
		mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", "demo-timeline-stage-one").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedStage\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(1))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(2_500_000))
			.andExpect(jsonPath("$.raid.progressBps").value(1_666))
			.andExpect(jsonPath("$.raid.coachCopyKey").value("RAID_STAGE_1_READY_V1"));
		String date = java.time.LocalDate.now().toString();
		mockMvc.perform(get("/api/v1/records/{date}", date).header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events[0].eventType").value("MYDATA_RECALCULATION"));
	}

	private ResultActions advance(String authorization, int expectedStage, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/demo/timeline/advance").header("Authorization", authorization)
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fixtureId\":\"EUROPE_TRAVEL_JANUARY\",\"expectedStage\":%d}".formatted(expectedStage)));
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
	private JsonNode response(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
}
