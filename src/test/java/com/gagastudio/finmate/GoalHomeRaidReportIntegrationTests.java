package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GoalHomeRaidReportIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;

	@Test
	void completingOnboardingPersistsTheConfirmedMainGoalAndBaseline() throws Exception {
		MvcResult signup = signUp("goal-onboarding@example.com");

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "onboarding-key-completion-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.displayName").value("Mina"))
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(2_000_000))
			.andExpect(jsonPath("$.mainGoal.targetAmountKrw").value(5_000_000))
			.andExpect(jsonPath("$.mainGoal.state").value("ACTIVE"))
			.andExpect(jsonPath("$.mainGoal.calculationVersion").value("goal-calc-v1"))
			.andExpect(jsonPath("$.mainGoal.dataState").value("FRESH"));

		mockMvc.perform(get("/api/v1/onboarding").header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.mainGoal.title").value("Europe travel fund"));
	}

	@Test
	void confirmedBaselineDrivesActiveGoalHomeRaidAndMonthlyReport() throws Exception {
		MvcResult signup = signUp("goal-views@example.com");
		completeOnboarding(signup);
		String authorization = "Bearer " + accessToken(signup);
		String month = java.time.YearMonth.now().toString();

		mockMvc.perform(get("/api/v1/goals/active").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentAmountKrw").value(2_000_000))
			.andExpect(jsonPath("$.targetAmountKrw").value(5_000_000));
		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.raid.progressBps").value(0))
			.andExpect(jsonPath("$.raid.xp").value(0))
			.andExpect(jsonPath("$.raid.financialStats.spendingBps").value(5_200))
			.andExpect(jsonPath("$.activeRoutineBuild").isEmpty())
			.andExpect(jsonPath("$.nextQuest").isEmpty());
		mockMvc.perform(get("/api/v1/raids/current").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(1))
			.andExpect(jsonPath("$.progressBps").value(0))
			.andExpect(jsonPath("$.coachCopyKey").value("RAID_STAGE_1_READY_V1"));
		mockMvc.perform(get("/api/v1/reports/monthly").queryParam("month", month).header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value(month))
			.andExpect(jsonPath("$.goalProgressBps").value(0))
			.andExpect(jsonPath("$.xpEarned").value(0))
			.andExpect(jsonPath("$.completedQuestCount").value(0));
	}

	@Test
	void rejectsASubsequentActiveMainGoal() throws Exception {
		MvcResult signup = signUp("goal-duplicate@example.com");
		completeOnboarding(signup);

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "onboarding-key-duplicate-02")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACTIVE_MAIN_GOAL_EXISTS"));
	}

	@Test
	void rejectsUnauthenticatedCalculatedReads() throws Exception {
		mockMvc.perform(get("/api/v1/goals/active"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void requiresAnIdempotencyKeyForOnboardingCompletion() throws Exception {
		MvcResult signup = signUp("goal-idempotency@example.com");

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "onboarding-key-retry-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "onboarding-key-retry-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void rejectsAnInvalidReportMonth() throws Exception {
		MvcResult signup = signUp("goal-report-validation@example.com");
		completeOnboarding(signup);

		mockMvc.perform(get("/api/v1/reports/monthly").queryParam("month", "2026-13")
				.header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	private void completeOnboarding(MvcResult signup) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "onboarding-key-helper-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk());
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private String accessToken(MvcResult result) throws Exception {
		return new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
	}

	private String onboardingBody() {
		return """
			{"displayName":"Mina","mainGoal":{"title":"Europe travel fund","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
			""";
	}
}
