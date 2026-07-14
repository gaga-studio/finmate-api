package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.goals.SyntheticSnapshotIngestionService;
import com.gagastudio.finmate.goals.SyntheticSnapshotInput;
import com.gagastudio.finmate.goals.SyntheticSnapshotResult;
import java.time.Instant;
import java.time.YearMonth;
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
class GoalHomeRaidReportIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	SyntheticSnapshotIngestionService snapshotIngestion;
	@Autowired
	JdbcTemplate jdbcTemplate;

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
			.andExpect(jsonPath("$.mainGoal.calculationVersion").value("goal-calc-v2"))
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
			.andExpect(jsonPath("$.raid.currentProgressBps").value(0))
			.andExpect(jsonPath("$.raid.financialStats.questXp").value(0))
			.andExpect(jsonPath("$.raid.financialStats.spendingDefenseBps").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.activeRoutineBuild").doesNotExist())
			.andExpect(jsonPath("$.nextQuest").doesNotExist());
		mockMvc.perform(get("/api/v1/raids/current").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stage").value(1))
			.andExpect(jsonPath("$.currentProgressBps").value(0))
			.andExpect(jsonPath("$.coachCopyKey").value("RAID_STAGE_1_WAITING_V2"));
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
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.instance").value("/api/v1/reports/monthly"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void ingestionRecalculatesGoalRaidAndAuditWithoutXpProgress() throws Exception {
		MvcResult signup = signUp("goal-ingestion@example.com");
		completeOnboarding(signup);
		UUID userId = userId(signup);
		Instant firstSync = Instant.now().plusSeconds(1);

		SyntheticSnapshotResult withoutXp = snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(3_500_000, 5_100, 2_100, 4_200, 0, firstSync));
		SyntheticSnapshotResult withXp = snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(3_500_000, 5_100, 2_100, 4_200, 9_999, firstSync.plusSeconds(1)));

		assertThat(withoutXp.currentProgressBps()).isEqualTo(5_000);
		assertThat(withXp.currentProgressBps()).isEqualTo(withoutXp.currentProgressBps());
		assertThat(withXp.highestProgressBps()).isEqualTo(5_000);
		assertThat(withXp.stage()).isEqualTo(2);
		assertThat(withXp.bossHpBps()).isEqualTo(4_849);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT current_amount_krw FROM finmate_user_goal WHERE user_id = ? AND state = 'ACTIVE'",
			Long.class, userId)).isEqualTo(3_500_000L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM finmate_raid_projection_audit WHERE raid_id = ?", Long.class, withXp.raidId()))
			.isEqualTo(2L);
	}

	@Test
	void oneHundredPercentLeavesStageThreeBossAtZeroHp() throws Exception {
		MvcResult signup = signUp("goal-complete@example.com");
		completeOnboarding(signup);

		SyntheticSnapshotResult result = snapshotIngestion.ingest(userId(signup),
			new SyntheticSnapshotInput(5_000_000, 4_800, 2_500, 4_500, 300, Instant.now().plusSeconds(1)));

		assertThat(result.currentProgressBps()).isEqualTo(10_000);
		assertThat(result.highestProgressBps()).isEqualTo(10_000);
		assertThat(result.stage()).isEqualTo(3);
		assertThat(result.bossHpBps()).isZero();
	}

	@Test
	void raidProgressDoesNotRelockOrHealAfterRegressionFromPeak() throws Exception {
		MvcResult signup = signUp("goal-regression@example.com");
		completeOnboarding(signup);
		UUID userId = userId(signup);
		Instant peakSync = Instant.now().plusSeconds(1);
		SyntheticSnapshotResult peak = snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(4_100_000, 5_000, 2_300, 4_300, 0, peakSync));

		SyntheticSnapshotResult regressed = snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(3_500_000, 5_300, 2_000, 4_100, 500, peakSync.plusSeconds(1)));

		assertThat(peak.highestProgressBps()).isEqualTo(7_000);
		assertThat(regressed.currentProgressBps()).isEqualTo(5_000);
		assertThat(regressed.highestProgressBps()).isEqualTo(7_000);
		assertThat(regressed.stage()).isEqualTo(3);
		assertThat(regressed.bossHpBps()).isEqualTo(8_824);
		mockMvc.perform(get("/api/v1/raids/current").header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.highestProgressBps").value(7_000))
			.andExpect(jsonPath("$.stage").value(3))
			.andExpect(jsonPath("$.bossHpBps").value(8_824));
	}

	@Test
	void twoUsersReceiveOnlyTheirOwnSyntheticProjections() throws Exception {
		MvcResult first = signUp("goal-isolation-a@example.com");
		MvcResult second = signUp("goal-isolation-b@example.com");
		completeOnboarding(first);
		completeOnboarding(second);
		Instant sync = Instant.now().plusSeconds(1);
		snapshotIngestion.ingest(userId(first), new SyntheticSnapshotInput(3_500_000, 5_000, 2_500, 4_000, 10, sync));
		snapshotIngestion.ingest(userId(second), new SyntheticSnapshotInput(2_300_000, 6_000, 1_000, 3_000, 900, sync));

		mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + accessToken(first)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(3_500_000))
			.andExpect(jsonPath("$.raid.currentProgressBps").value(5_000))
			.andExpect(jsonPath("$.raid.financialStats.questXp").value(0));
		mockMvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + accessToken(second)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mainGoal.currentAmountKrw").value(2_300_000))
			.andExpect(jsonPath("$.raid.currentProgressBps").value(1_000))
			.andExpect(jsonPath("$.raid.financialStats.questXp").value(0));
	}

	@Test
	void databaseRejectsCrossUserGoalOwnership() throws Exception {
		MvcResult owner = signUp("goal-owner@example.com");
		MvcResult other = signUp("goal-other@example.com");
		completeOnboarding(owner);
		completeOnboarding(other);
		UUID ownerId = userId(owner);
		UUID otherId = userId(other);
		UUID ownerGoalId = activeGoalId(ownerId);
		UUID badSnapshotId = UUID.randomUUID();

		try {
			assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO finmate_synthetic_financial_snapshot
				(id, user_id, goal_id, snapshot_month, observed_goal_amount_krw, spending_bps, saving_bps, investment_judgment_bps, xp, last_synced_at)
				VALUES (?, ?, ?, CURRENT_DATE, 2500000, 5000, 2000, 4000, 0, CURRENT_TIMESTAMP)
				""", badSnapshotId, otherId, ownerGoalId))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbcTemplate.update("DELETE FROM finmate_synthetic_financial_snapshot WHERE id = ?", badSnapshotId);
		}
		try {
			assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE finmate_raid_projection SET user_id = ? WHERE goal_id = ?", otherId, ownerGoalId))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbcTemplate.update("UPDATE finmate_raid_projection SET user_id = ? WHERE goal_id = ?", ownerId, ownerGoalId);
		}
	}

	@Test
	void missingRequiredAmountReturnsValidationProblem() throws Exception {
		MvcResult signup = signUp("goal-missing-amount@example.com");

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "missing-amount-key-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"Mina","mainGoal":{"title":"Europe travel fund","domain":"SAVING","targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'mainGoal.currentAmountKrw')]").exists());
	}

	@Test
	void oversizedGoalTitleReturnsValidationProblem() throws Exception {
		MvcResult signup = signUp("goal-title-size@example.com");
		String oversizedTitle = "x".repeat(256);

		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "oversized-title-key-01")
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody().replace("Europe travel fund", oversizedTitle)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void missingSnapshotReturnsInsufficientCalculatedViews() throws Exception {
		MvcResult signup = signUp("goal-no-snapshot@example.com");
		completeOnboarding(signup);
		jdbcTemplate.update("DELETE FROM finmate_synthetic_financial_snapshot WHERE user_id = ?", userId(signup));
		String authorization = "Bearer " + accessToken(signup);

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.lastSyncedAt").isEmpty())
			.andExpect(jsonPath("$.raid.dataState").value("INSUFFICIENT"));
		mockMvc.perform(get("/api/v1/raids/current").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.lastSyncedAt").isEmpty());
		mockMvc.perform(get("/api/v1/reports/monthly").queryParam("month", YearMonth.now().toString())
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.goalProgressBps").value(0));
	}

	@Test
	void monthlyReportIgnoresSnapshotsFromAHistoricalGoal() throws Exception {
		MvcResult signup = signUp("goal-history@example.com");
		completeOnboarding(signup);
		UUID userId = userId(signup);
		snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(3_500_000, 5_000, 2_500, 4_000, 0, Instant.now().plusSeconds(60)));
		jdbcTemplate.update("UPDATE finmate_user_goal SET state = 'ARCHIVED' WHERE user_id = ? AND state = 'ACTIVE'", userId);

		confirmGoal(signup, "historical-goal-key-002");

		mockMvc.perform(get("/api/v1/reports/monthly").queryParam("month", YearMonth.now().toString())
				.header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.goalProgressBps").value(0))
			.andExpect(jsonPath("$.financialStats.spendingDefenseBps").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
	}

	@Test
	void missingGoalReturnsSharedContractProblem() throws Exception {
		MvcResult signup = signUp("goal-missing@example.com");

		mockMvc.perform(get("/api/v1/goals/active").header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/api/v1/goals/active"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void missingGoalReportReturnsCanonicalNotFoundProblem() throws Exception {
		MvcResult signup = signUp("goal-report-missing@example.com");

		mockMvc.perform(get("/api/v1/reports/monthly").queryParam("month", YearMonth.now().toString())
				.header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.instance").value("/api/v1/reports/monthly"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void sameKeyConcurrentOnboardingCreatesOneGoalAndWaitingRaidWithoutInventingABaseline() throws Exception {
		MvcResult signup = signUp("goal-concurrent-same@example.com");
		List<MvcResult> results = concurrentOnboarding(signup, "concurrent-same-key-01", "concurrent-same-key-01");

		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 200);
		UUID userId = userId(signup);
		assertThat(count("finmate_user_goal", userId)).isEqualTo(1L);
		assertThat(count("finmate_synthetic_financial_snapshot", userId)).isZero();
		assertThat(count("finmate_raid_projection", userId)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM finmate_raid_projection_audit audit
			JOIN finmate_raid_projection raid ON raid.id = audit.raid_id
			WHERE raid.user_id = ?
			""", Long.class, userId)).isZero();
	}

	@Test
	void differentKeyConcurrentOnboardingReturnsCleanConflict() throws Exception {
		MvcResult signup = signUp("goal-concurrent-different@example.com");
		List<MvcResult> results = concurrentOnboarding(signup, "concurrent-key-first-01", "concurrent-key-second-02");

		assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 409);
		MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
		assertThat(new ObjectMapper().readTree(conflict.getResponse().getContentAsString()).path("code").asText())
			.isEqualTo("ACTIVE_MAIN_GOAL_EXISTS");
		assertThat(new ObjectMapper().readTree(conflict.getResponse().getContentAsString()).path("traceId").asText())
			.isNotBlank();
	}

	private void completeOnboarding(MvcResult signup) throws Exception {
		completeOnboarding(signup, "onboarding-key-helper-0001");
	}

	private void completeOnboarding(MvcResult signup, String idempotencyKey) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk());
	}

	private void confirmGoal(MvcResult signup, String idempotencyKey) throws Exception {
		mockMvc.perform(post("/api/v1/goals")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"goal":{"title":"Europe travel fund","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirm":true}
					"""))
			.andExpect(status().isCreated());
	}

	private List<MvcResult> concurrentOnboarding(MvcResult signup, String firstKey, String secondKey) throws Exception {
		String token = accessToken(signup);
		CyclicBarrier start = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<MvcResult> first = () -> onboardingRequest(token, firstKey, start);
			Callable<MvcResult> second = () -> onboardingRequest(token, secondKey, start);
			List<Future<MvcResult>> responses = executor.invokeAll(List.of(first, second));
			return List.of(responses.get(0).get(), responses.get(1).get());
		} finally {
			executor.shutdownNow();
		}
	}

	private MvcResult onboardingRequest(String accessToken, String idempotencyKey, CyclicBarrier start) throws Exception {
		start.await();
		return mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andReturn();
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

	private UUID userId(MvcResult result) throws Exception {
		return UUID.fromString(new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("user").path("userId").asText());
	}

	private UUID activeGoalId(UUID userId) {
		return jdbcTemplate.queryForObject(
			"SELECT id FROM finmate_user_goal WHERE user_id = ? AND state = 'ACTIVE'", UUID.class, userId);
	}

	private long count(String table, UUID userId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
	}

	private String onboardingBody() {
		return """
			{"displayName":"Mina","mainGoal":{"title":"Europe travel fund","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
			""";
	}
}
