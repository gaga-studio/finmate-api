package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class MateRoutineBuildIntegrationTests {
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
	void databaseRejectsInvalidMateGroupThresholdVariants() {
		assertThatThrownBy(() -> insertGroup("invalid-operational-29", 29, false, true))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGroup("invalid-production-10", 10, false, true))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGroup("invalid-demo-eligible", 10, true, true))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGroup("invalid-demo-size", 11, true, false))
			.isInstanceOf(DataIntegrityViolationException.class);
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

	@Test
	void delayedImportReplayReturnsTheImmutableOriginalRepresentation() throws Exception {
		String authorization = authorization(signUp("mate-import-replay@example.com"));
		String firstAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String importKey = "mate-import-delayed-replay-01";
		MvcResult original = mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import",
				firstAdaptation, "candidate-light")
				.header("Authorization", authorization).header("Idempotency-Key", importKey))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn();

		String secondAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		mockMvc.perform(post("/api/v1/routine-builds/active/replacement")
				.header("Authorization", authorization).header("Idempotency-Key", "mate-import-replay-replace-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adaptationId\":\"%s\",\"candidateId\":\"candidate-standard\",\"confirmReplacement\":true}"
					.formatted(secondAdaptation)))
			.andExpect(status().isOk());

		MvcResult replay = mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import",
				firstAdaptation, "candidate-light")
				.header("Authorization", authorization).header("Idempotency-Key", importKey))
			.andExpect(status().isCreated())
			.andReturn();
		assertThatJsonEquals(response(original), response(replay));
	}

	@Test
	void delayedReplacementReplayReturnsTheImmutableOriginalRepresentation() throws Exception {
		String authorization = authorization(signUp("mate-replacement-replay@example.com"));
		String importedAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		importCandidate(importedAdaptation, "candidate-light", "mate-replacement-base-0001", authorization);

		String firstReplacementAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String firstReplacementKey = "mate-replacement-delayed-001";
		MvcResult original = replace(firstReplacementAdaptation, "candidate-standard", firstReplacementKey, true, authorization);
		String secondReplacementAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		replace(secondReplacementAdaptation, "candidate-challenge", "mate-replacement-later-0001", true, authorization);

		MvcResult replay = replace(firstReplacementAdaptation, "candidate-standard", firstReplacementKey, true, authorization);
		assertThatJsonEquals(response(original), response(replay));
	}

	@Test
	void crossUserAdaptationAndActiveBuildAccessIsRejected() throws Exception {
		String ownerAuthorization = authorization(signUp("mate-owner@example.com"));
		String otherAuthorization = authorization(signUp("mate-other@example.com"));
		String ownerAdaptation = createAdaptation(ownerAuthorization);

		mockMvc.perform(put("/api/v1/routine-adaptations/{adaptationId}/choice", ownerAdaptation)
				.header("Authorization", otherAuthorization).contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\":\"SAVING\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		chooseSaving(ownerAdaptation, ownerAuthorization);
		String ownerBuildId = importCandidate(ownerAdaptation, "candidate-light", "mate-owner-import-key-0001", ownerAuthorization);
		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", otherAuthorization))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import",
				ownerAdaptation, "candidate-light")
				.header("Authorization", otherAuthorization).header("Idempotency-Key", "mate-other-import-key-0001"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", ownerAuthorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.buildId").value(ownerBuildId));
	}

	@Test
	void importKeyReuseWithDifferentRequestReturnsConflictWithoutReplacingTheActiveBuild() throws Exception {
		String authorization = authorization(signUp("mate-import-key-mismatch@example.com"));
		String firstAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String key = "mate-import-key-mismatch-0001";
		String buildId = importCandidate(firstAdaptation, "candidate-light", key, authorization);
		String secondAdaptation = chooseSaving(createAdaptation(authorization), authorization);

		mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import",
				secondAdaptation, "candidate-standard")
				.header("Authorization", authorization).header("Idempotency-Key", key))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.buildId").value(buildId));
	}

	@Test
	void replacementKeyReuseWithDifferentRequestReturnsConflictWithoutMutation() throws Exception {
		String authorization = authorization(signUp("mate-replace-key-mismatch@example.com"));
		String importedAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		importCandidate(importedAdaptation, "candidate-light", "mate-replace-mismatch-base-01", authorization);
		String firstAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String key = "mate-replace-key-mismatch-001";
		MvcResult replacement = replace(firstAdaptation, "candidate-standard", key, true, authorization);
		String activeBuildId = response(replacement).path("activeBuild").path("buildId").asText();
		String secondAdaptation = chooseSaving(createAdaptation(authorization), authorization);

		mockMvc.perform(post("/api/v1/routine-builds/active/replacement")
				.header("Authorization", authorization).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adaptationId\":\"%s\",\"candidateId\":\"candidate-challenge\",\"confirmReplacement\":true}"
					.formatted(secondAdaptation)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.buildId").value(activeBuildId));
	}

	@Test
	void falseReplacementConfirmationRejectsWithoutAnyMutation() throws Exception {
		MvcResult signup = signUp("mate-replace-false@example.com");
		String authorization = authorization(signup);
		String importedAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		importCandidate(importedAdaptation, "candidate-light", "mate-replace-false-base-001", authorization);
		JsonNode activeBefore = response(mockMvc.perform(get("/api/v1/routine-builds/active")
			.header("Authorization", authorization)).andReturn());
		String replacementAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String falseKey = "mate-replace-false-key-0001";
		int buildCountBefore = buildCount(userId(signup));

		mockMvc.perform(post("/api/v1/routine-builds/active/replacement")
				.header("Authorization", authorization).header("Idempotency-Key", falseKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adaptationId\":\"%s\",\"candidateId\":\"candidate-standard\",\"confirmReplacement\":false}"
					.formatted(replacementAdaptation)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThatJsonEquals(activeBefore, response(mockMvc.perform(get("/api/v1/routine-builds/active")
			.header("Authorization", authorization)).andReturn()));
		assertThat(buildCount(userId(signup))).isEqualTo(buildCountBefore);
		assertThat(commandCount(userId(signup), "REPLACE", falseKey)).isZero();
	}

	@Test
	void commandRowsStoreOriginalStatusBodyAndResultIdentifiersAndRejectMutation() throws Exception {
		MvcResult signup = signUp("mate-command-row@example.com");
		String authorization = authorization(signup);
		String adaptation = chooseSaving(createAdaptation(authorization), authorization);
		String key = "mate-command-row-import-0001";
		MvcResult original = performImport(adaptation, "candidate-light", key, authorization);
		JsonNode originalBody = response(original);

		Map<String, Object> command = jdbcTemplate.queryForMap("""
			SELECT original_status, original_body, result_build_id, archived_build_id, active_build_id
			FROM finmate_routine_idempotency_command
			WHERE user_id = ? AND operation = 'IMPORT' AND idempotency_key = ?
			""", UUID.fromString(userId(signup)), key);
		assertThat(command.get("original_status")).isEqualTo(201);
		assertThat(objectMapper.readTree((String) command.get("original_body"))).isEqualTo(originalBody);
		assertThat(command.get("result_build_id").toString()).isEqualTo(originalBody.path("buildId").asText());
		assertThat(command.get("archived_build_id")).isNull();
		assertThat(command.get("active_build_id")).isNull();
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE finmate_routine_idempotency_command SET original_status = 202
			WHERE user_id = ? AND operation = 'IMPORT' AND idempotency_key = ?
			""", UUID.fromString(userId(signup)), key)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void concurrentImportsSerializeToOneCreatedBuildAndOneConflict() throws Exception {
		String authorization = authorization(signUp("mate-concurrent-import@example.com"));
		String firstAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String secondAdaptation = chooseSaving(createAdaptation(authorization), authorization);

		List<MvcResult> results = concurrently(
			() -> performImport(firstAdaptation, "candidate-light", "mate-concurrent-import-key-01", authorization),
			() -> performImport(secondAdaptation, "candidate-standard", "mate-concurrent-import-key-02", authorization));

		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
		MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
		assertThat(response(conflict).path("code").asText()).isEqualTo("ACTIVE_ROUTINE_BUILD_EXISTS");
	}

	@Test
	void concurrentReplacementRetriesReplayOneOriginalResult() throws Exception {
		MvcResult signup = signUp("mate-concurrent-replace@example.com");
		String authorization = authorization(signup);
		String importedAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		importCandidate(importedAdaptation, "candidate-light", "mate-concurrent-replace-base-1", authorization);
		String replacementAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String key = "mate-concurrent-replace-key-01";

		List<MvcResult> results = concurrently(
			() -> performReplacement(replacementAdaptation, "candidate-standard", key, true, authorization),
			() -> performReplacement(replacementAdaptation, "candidate-standard", key, true, authorization));

		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsOnly(200);
		assertThatJsonEquals(response(results.get(0)), response(results.get(1)));
		assertThat(commandCount(userId(signup), "REPLACE", key)).isEqualTo(1);
	}

	@Test
	void concurrentImportAndReplacementSerializeWithoutServerError() throws Exception {
		String authorization = authorization(signUp("mate-concurrent-mixed@example.com"));
		String importedAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		importCandidate(importedAdaptation, "candidate-light", "mate-concurrent-mixed-base-01", authorization);
		String importAdaptation = chooseSaving(createAdaptation(authorization), authorization);
		String replacementAdaptation = chooseSaving(createAdaptation(authorization), authorization);

		List<MvcResult> results = concurrently(
			() -> performImport(importAdaptation, "candidate-standard", "mate-concurrent-mixed-import-1", authorization),
			() -> performReplacement(replacementAdaptation, "candidate-challenge", "mate-concurrent-mixed-replace1", true,
				authorization));

		assertThat(results.get(0).getResponse().getStatus()).isEqualTo(409);
		assertThat(response(results.get(0)).path("code").asText()).isEqualTo("ACTIVE_ROUTINE_BUILD_EXISTS");
		assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
		String activeBuildId = response(results.get(1)).path("activeBuild").path("buildId").asText();
		mockMvc.perform(get("/api/v1/routine-builds/active").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.buildId").value(activeBuildId));
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
		MvcResult result = performImport(adaptationId, candidateId, idempotencyKey, authorization);
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return response(result).path("buildId").asText();
	}

	private MvcResult replace(String adaptationId, String candidateId, String idempotencyKey, boolean confirmReplacement,
		String authorization) throws Exception {
		MvcResult result = performReplacement(adaptationId, candidateId, idempotencyKey, confirmReplacement, authorization);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return result;
	}

	private MvcResult performImport(String adaptationId, String candidateId, String idempotencyKey, String authorization) throws Exception {
		return mockMvc.perform(post("/api/v1/routine-adaptations/{adaptationId}/candidates/{candidateId}/import", adaptationId, candidateId)
				.header("Authorization", authorization).header("Idempotency-Key", idempotencyKey))
			.andReturn();
	}

	private MvcResult performReplacement(String adaptationId, String candidateId, String idempotencyKey,
		boolean confirmReplacement, String authorization) throws Exception {
		return mockMvc.perform(post("/api/v1/routine-builds/active/replacement")
				.header("Authorization", authorization).header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"adaptationId\":\"%s\",\"candidateId\":\"%s\",\"confirmReplacement\":%s}"
					.formatted(adaptationId, candidateId, confirmReplacement)))
			.andReturn();
	}

	private void insertGroup(String groupId, int memberCount, boolean syntheticDemo, boolean eligible) {
		jdbcTemplate.update("""
			INSERT INTO finmate_mate_group (id, name, member_count, synthetic_demo, eligible_for_production_aggregation)
			VALUES (?, 'Invalid threshold fixture', ?, ?, ?)
			""", groupId, memberCount, syntheticDemo, eligible);
	}

	private int buildCount(String userId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM finmate_routine_build WHERE user_id = ?", Integer.class,
			UUID.fromString(userId));
	}

	private int commandCount(String userId, String operation, String idempotencyKey) {
		return jdbcTemplate.queryForObject("""
			SELECT count(*) FROM finmate_routine_idempotency_command
			WHERE user_id = ? AND operation = ? AND idempotency_key = ?
			""", Integer.class, UUID.fromString(userId), operation, idempotencyKey);
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
		return () -> {
			barrier.await();
			return request.call();
		};
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

	private String userId(MvcResult signup) throws Exception {
		return response(signup).path("user").path("userId").asText();
	}

	private JsonNode response(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void assertThatJsonEquals(JsonNode expected, JsonNode actual) {
		org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
	}
}
