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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MateRoutineBuildIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void listsOnlyEligibleOperationalGroupsAndAnExplicitSyntheticDemoGroup() throws Exception {
		String authorization = authorization(signUp("mate-groups@example.com"));

		mockMvc.perform(get("/api/v1/mate/groups").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].groupId").value("group-demo-10"))
			.andExpect(jsonPath("$.items[0].memberCount").value(10))
			.andExpect(jsonPath("$.items[0].syntheticDemo").value(true))
			.andExpect(jsonPath("$.items[0].eligibleForProductionAggregation").value(false))
			.andExpect(jsonPath("$.items[1].groupId").value("group-saving-30"))
			.andExpect(jsonPath("$.items[1].memberCount").value(34))
			.andExpect(jsonPath("$.items[1].syntheticDemo").value(false))
			.andExpect(jsonPath("$.items[1].eligibleForProductionAggregation").value(true));

		mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].alias").value("Cobalt Compass"))
			.andExpect(jsonPath("$.items[0].similarityReasons[0]").value("Travel goal"))
			.andExpect(jsonPath("$.items[0].routines[0].routineId").value("routine-weekly-save"))
			.andExpect(jsonPath("$.items[0].balance").doesNotExist())
			.andExpect(jsonPath("$.items[0].account").doesNotExist())
			.andExpect(jsonPath("$.items[0].securities").doesNotExist());
	}

	@Test
	void rejectsNestedIdsThatDoNotBelongToTheSelectedGroupAndAdventurer() throws Exception {
		String authorization = authorization(signUp("mate-nested@example.com"));

		mockMvc.perform(get("/api/v1/mate/groups/group-demo-10/adventurers/adv-cobalt/routines/routine-weekly-save")
				.header("Authorization", authorization))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void createsDomainChoiceWithExactlyThreeCandidatesAndDomainRestrictions() throws Exception {
		String authorization = authorization(signUp("mate-adaptation@example.com"));
		String adaptationId = createAdaptation(authorization);
		mockMvc.perform(put("/api/v1/routine-adaptations/{adaptationId}/choice", adaptationId)
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\":\"FINANCIAL_KNOWLEDGE\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ADAPTATION_DOMAIN_REQUIRED"));

		mockMvc.perform(put("/api/v1/routine-adaptations/{adaptationId}/choice", adaptationId)
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\":\"INVESTMENT_JUDGMENT\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("CANDIDATES_READY"))
			.andExpect(jsonPath("$.selectedDomain").value("INVESTMENT_JUDGMENT"))
			.andExpect(jsonPath("$.light.difficulty").value("LIGHT"))
			.andExpect(jsonPath("$.standard.difficulty").value("STANDARD"))
			.andExpect(jsonPath("$.challenge.difficulty").value("CHALLENGE"))
			.andExpect(jsonPath("$.light.targetKind").value("BEHAVIOR"))
			.andExpect(jsonPath("$.light.targetAmountKrw").doesNotExist())
			.andExpect(jsonPath("$.light.targetRatioBps").doesNotExist());
	}

	@Test
	void importConflictAndConfirmedReplacementPreserveGoalAndArchiveHistory() throws Exception {
		MvcResult signup = signUp("mate-build@example.com");
		String authorization = authorization(signup);
		completeOnboarding(authorization);
		JsonNode goalBefore = response(mockMvc.perform(get("/api/v1/goals/active").header("Authorization", authorization)).andReturn());

		String firstAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String firstBuildId = importCandidate(firstAdaptation, "candidate-light", "mate-import-key-first-0001", authorization);
		mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import", firstAdaptation, "candidate-light")
				.header("Authorization", authorization).header("Idempotency-Key", "mate-import-key-first-0001"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.buildId").value(firstBuildId));

		String secondAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import", secondAdaptation, "candidate-standard")
				.header("Authorization", authorization).header("Idempotency-Key", "mate-import-key-conflict-001"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACTIVE_ROUTINE_BUILD_EXISTS"));

		mockMvc.perform(post("/api/v1/routine-builds/active/replacement")
				.header("Authorization", authorization).header("Idempotency-Key", "mate-replace-key-confirm-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adaptationId\":\"%s\",\"candidateId\":\"candidate-standard\",\"confirmReplacement\":true}".formatted(secondAdaptation)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedBuild.buildId").value(firstBuildId))
			.andExpect(jsonPath("$.archivedBuild.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.archivedBuild.replacedByBuildId").isNotEmpty())
			.andExpect(jsonPath("$.activeBuild.status").value("ACTIVE"))
			.andExpect(jsonPath("$.activeBuild.replacesBuildId").value(firstBuildId));

		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.replacesBuildId").value(firstBuildId));
		assertThatJsonEquals(goalBefore,
			response(mockMvc.perform(get("/api/v1/goals/active").header("Authorization", authorization)).andReturn()));
	}

	private String createAdaptation(String authorization) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/routine-adaptations").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"groupId\":\"group-saving-30\",\"adventurerId\":\"adv-cobalt\",\"routineId\":\"routine-weekly-save\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("AWAITING_DOMAIN"))
			.andReturn();
		return response(result).path("adaptationId").asText();
	}

	private String chooseSaving(String adaptationId, String authorization) throws Exception {
		mockMvc.perform(put("/api/v1/routine-adaptations/{adaptationId}/choice", adaptationId)
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{\"domain\":\"SAVING\"}"))
			.andExpect(status().isOk());
		return adaptationId;
	}

	private String importCandidate(String adaptationId, String candidateId, String idempotencyKey, String authorization) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import", adaptationId, candidateId)
				.header("Authorization", authorization).header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isCreated()).andReturn();
		return response(result).path("buildId").asText();
	}

	private void completeOnboarding(String authorization) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding").header("Authorization", authorization)
				.header("Idempotency-Key", "mate-onboarding-key-0001").contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"Mina\",\"mainGoal\":{\"title\":\"Europe travel fund\",\"domain\":\"SAVING\",\"currentAmountKrw\":2000000,\"targetAmountKrw\":5000000,\"targetMonth\":\"2027-01\"},\"confirmMainGoal\":true}"))
			.andExpect(status().isOk());
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

	private void assertThatJsonEquals(JsonNode expected, JsonNode actual) {
		org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
	}
}
