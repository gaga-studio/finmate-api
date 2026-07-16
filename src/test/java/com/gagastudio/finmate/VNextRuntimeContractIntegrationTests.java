package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.records.RecordService;
import com.gagastudio.finmate.goals.SyntheticSnapshotIngestionService;
import com.gagastudio.finmate.goals.SyntheticSnapshotInput;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import java.sql.Timestamp;
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
class VNextRuntimeContractIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	RecordService recordService;
	@Autowired
	SyntheticSnapshotIngestionService snapshotIngestion;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void initialOnboardingKeepsTheRequiredNullableSyncField() throws Exception {
		String authorization = authorization(signUp("vnext-initial-onboarding@example.com"));

		mockMvc.perform(get("/api/v1/onboarding").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.displayName").doesNotExist())
			.andExpect(jsonPath("$.context").doesNotExist())
			.andExpect(jsonPath("$.lastSyncedAt").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void completesExploreOnlyOnboardingThenConfirmsGoalSeparately() throws Exception {
		String authorization = authorization(signUp("vnext-goal@example.com"));

		completeExploreOnlyOnboarding(authorization, "vnext-onboarding-goal-0001")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.onboardingState").value("EXPLORE_ONLY"))
			.andExpect(jsonPath("$.context.housingType").value("RENT"))
			.andExpect(jsonPath("$.baseline.disposableIncomeKrw").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.mainGoal").doesNotExist());

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("EXPLORE_ONLY"))
			.andExpect(jsonPath("$.mainGoal").doesNotExist())
			.andExpect(jsonPath("$.raid").doesNotExist())
			.andExpect(jsonPath("$.totalAssetsKrw").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.financialStats.savingHpBps").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.lockedActions.length()").value(4));

		confirmGoal(authorization, "vnext-goal-confirm-000001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("유럽여행경비"))
			.andExpect(jsonPath("$.currentAmountKrw").value(2_000_000))
			.andExpect(jsonPath("$.targetAmountKrw").value(5_000_000));

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("GOAL_ACTIVE"))
			.andExpect(jsonPath("$.raid.currentProgressBps").value(0))
			.andExpect(jsonPath("$.raid.highestProgressBps").value(0))
			.andExpect(jsonPath("$.raid.status").value("WAITING_FOR_DATA"))
			.andExpect(jsonPath("$.lockedActions").isEmpty());

		mockMvc.perform(get("/api/v1/reports/characters/SAVING_HP").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.characterName").value("SEAL"))
			.andExpect(jsonPath("$.scoreBps").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.metrics").isEmpty())
			.andExpect(jsonPath("$.trend30Days").isEmpty())
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
	}

	@Test
	void consentedOnboardingBindsOneCompatibleSyntheticRuntimePersonaWithoutReplacingIt() throws Exception {
		jdbcTemplate.update("""
			INSERT INTO finmate_import_persona
				(source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
				 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
				 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
				 joined_at, source_data_range, source_data_as_of, synthetic)
			VALUES ('P-RUNTIME-BIND-1', 'v1.0.0', '25-29', '20s', 'starter', 'EARLY_CAREER',
				 2500000, 'REGULAR', 2000, 1000, 3, 'BALANCED', 'RENT', '[]', 'SAVE', 'SAVING',
				 DATE '2026-01-01', '2026-01~2026-07', DATE '2026-07-13', TRUE)
			ON CONFLICT (source_persona_id) DO NOTHING
			""");
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_persona
				(source_persona_id, release_version, age_band, cohort, occupation_group, income_regularity,
				 income_band, spending_tendency, saving_rate_band, investment_tendency, household_type,
				 lifestyle_tags, money_worry, peer_discovery_opt_in, data_state, last_synced_at,
				 visible_fields, exact_values)
			VALUES ('P-RUNTIME-BIND-1', 'v1.0.0', 'AGE_24_29', '20s', 'EARLY_CAREER', 'REGULAR',
				 'FROM_200_TO_300', 'BALANCED', 'FROM_10_TO_20', 'BALANCED', 'RENT',
				 '[]', 'SAVING', TRUE, 'FRESH', TIMESTAMPTZ '2026-07-13 00:00:00Z', '[]', FALSE)
			ON CONFLICT (source_persona_id, release_version) DO NOTHING
			""");

		MvcResult signup = signUp("vnext-runtime-binding@example.com");
		String authorization = authorization(signup);
		UUID userId = UUID.fromString(response(signup).path("user").path("userId").asText());
		completeExploreOnlyOnboarding(authorization, "vnext-runtime-binding-onboarding")
			.andExpect(status().isOk());

		String boundPersona = jdbcTemplate.queryForObject("""
			SELECT source_persona_id FROM finmate_user_synthetic_persona_binding WHERE user_id = ?
			""", String.class, userId);
		org.junit.jupiter.api.Assertions.assertEquals("P-RUNTIME-BIND-1", boundPersona);
		Integer bindings = jdbcTemplate.queryForObject("""
			SELECT count(*) FROM finmate_user_synthetic_persona_binding WHERE user_id = ?
			""", Integer.class, userId);
		org.junit.jupiter.api.Assertions.assertEquals(1, bindings);
		Boolean anonymousCardOptIn = jdbcTemplate.queryForObject("""
			SELECT anonymous_card_opt_in FROM finmate_user WHERE id = ?
			""", Boolean.class, userId);
		org.junit.jupiter.api.Assertions.assertEquals(true, anonymousCardOptIn);

		completeExploreOnlyOnboarding(authorization, "vnext-runtime-binding-onboarding")
			.andExpect(status().isOk());
		org.junit.jupiter.api.Assertions.assertEquals("P-RUNTIME-BIND-1", jdbcTemplate.queryForObject("""
			SELECT source_persona_id FROM finmate_user_synthetic_persona_binding WHERE user_id = ?
			""", String.class, userId));
	}

	@Test
	void acceptsThenCompletesAQuestWithoutChangingFinancialStats() throws Exception {
		String authorization = activeGoalAuthorization("vnext-quest@example.com");
		JsonNode quests = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn());
		String questId = quests.path("items").get(0).path("questId").asText();

		mockMvc.perform(post("/api/v1/quests/{questId}/accept", questId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "vnext-quest-accept-key-001"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quest.status").value("ACTIVE"))
			.andExpect(jsonPath("$.quest.currentValue").value(0))
			.andExpect(jsonPath("$.quest.targetValue").value(1))
			.andExpect(jsonPath("$.financialStatsChanged").value(false));

		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "vnext-quest-complete-key-01"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quest.status").value("COMPLETED"))
			.andExpect(jsonPath("$.xpAwarded").value(10))
			.andExpect(jsonPath("$.financialStatsChanged").value(false));

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.financialStats.questXp").value(10));

		MvcResult birdReport = mockMvc.perform(get("/api/v1/reports/characters/QUEST_XP")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scoreBps").value(1_000))
			.andExpect(jsonPath("$.metrics[0].displayValue").value("10"))
			.andReturn();
		String nextQuestId = response(birdReport).path("nextQuestId").asText();
		mockMvc.perform(get("/api/v1/quests/{questId}", nextQuestId).header("Authorization", authorization))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/reports/monthly").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.xpEarned").value(10))
			.andExpect(jsonPath("$.completedQuestCount").value(1));
	}

	@Test
	void monthlyReportCountsQuestRewardsOnlyInTheRequestedMonth() throws Exception {
		String authorization = activeGoalAuthorization("vnext-monthly-quest-scope@example.com");
		JsonNode questPage = response(mockMvc.perform(get("/api/v1/quests")
			.header("Authorization", authorization)).andReturn());
		String questId = questPage.path("items").get(0).path("questId").asText();

		mockMvc.perform(post("/api/v1/quests/{questId}/accept", questId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "monthly-scope-accept-0001"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/quests/{questId}/complete", questId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "monthly-scope-complete-001"))
			.andExpect(status().isOk());

		jdbcTemplate.update("""
			UPDATE finmate_quest_completion
			SET completed_at = TIMESTAMPTZ '2026-06-15 03:00:00Z'
			WHERE quest_id = ?::uuid
			""", questId);

		mockMvc.perform(get("/api/v1/reports/monthly").header("Authorization", authorization)
				.queryParam("month", "2026-06"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.xpEarned").value(10))
			.andExpect(jsonPath("$.completedQuestCount").value(1));
		mockMvc.perform(get("/api/v1/reports/monthly").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.xpEarned").value(0))
			.andExpect(jsonPath("$.completedQuestCount").value(0));
	}

	@Test
	void rejectsQuestAcceptIdempotencyKeyReuseAcrossDifferentQuests() throws Exception {
		String authorization = activeGoalAuthorization("vnext-quest-idempotency@example.com");
		JsonNode quests = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn());
		String firstQuestId = quests.path("items").get(0).path("questId").asText();
		String secondQuestId = quests.path("items").get(1).path("questId").asText();
		String idempotencyKey = "vnext-shared-quest-accept-key";

		mockMvc.perform(post("/api/v1/quests/{questId}/accept", firstQuestId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/quests/{questId}/accept", secondQuestId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void exploreOnlyModeCannotBypassGoalLockWithLegacyRoutineRequest() throws Exception {
		String authorization = authorization(signUp("vnext-explore-lock@example.com"));
		completeExploreOnlyOnboarding(authorization, "vnext-explore-lock-onboarding").andExpect(status().isOk());
		JsonNode quests = response(mockMvc.perform(get("/api/v1/quests").header("Authorization", authorization)).andReturn());
		String questId = quests.path("items").get(0).path("questId").asText();

		mockMvc.perform(post("/api/v1/quests/{questId}/accept", questId)
				.header("Authorization", authorization)
				.header("Idempotency-Key", "vnext-explore-quest-key-001"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GOAL_REQUIRED"));

		mockMvc.perform(post("/api/v1/routine-adaptations")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"groupId":"group-saving-30","adventurerId":"adv-cobalt","routineId":"routine-weekly-save"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GOAL_REQUIRED"));
	}

	@Test
	void exposesMateReportsSearchRecommendationAndReviewedProductInfo() throws Exception {
		String authorization = activeGoalAuthorization("vnext-mate@example.com");

		mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/report").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.group.groupId").value("group-saving-30"))
			.andExpect(jsonPath("$.savingRateRange.medianBps").isNumber())
			.andExpect(jsonPath("$.adventurerPreview").isNotEmpty());

		mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.adventurerId").value("adv-cobalt"))
			.andExpect(jsonPath("$.routines[0].maintenanceDays").isNumber());

		mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/report")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comparisonMetrics[0].myRange").value("18%"))
			.andExpect(jsonPath("$.comparisonMetrics").isNotEmpty())
			.andExpect(jsonPath("$.routineEvidence").isNotEmpty());

		mockMvc.perform(post("/api/v1/mate/explore/search").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"ageBand":"AGE_24_29","occupationGroup":"EARLY_CAREER","incomeBand":"FROM_200_TO_300","spendingTendency":"BALANCED","savingRateBand":"FROM_10_TO_20","investmentTendency":"BALANCED"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalEligible").value(0))
			.andExpect(jsonPath("$.matchMode").value("NONE"))
			.andExpect(jsonPath("$.calculationVersion").value("mate-search-runtime-v1"));

		mockMvc.perform(post("/api/v1/mate/explore/search").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"ageBand":"AGE_30_34","occupationGroup":"FREELANCER","incomeBand":"OVER_300","spendingTendency":"VARIABLE","savingRateBand":"UNDER_10","investmentTendency":"LEARNING"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalEligible").value(0))
			.andExpect(jsonPath("$.matchMode").value("NONE"));

		MvcResult recommendation = mockMvc.perform(post("/api/v1/routine-adaptations")
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"groupId":"group-saving-30","adventurerId":"adv-cobalt","sourceRoutineId":"routine-weekly-save","selectedDomain":"SAVING"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recommendedCandidate.difficulty").value("STANDARD"))
			.andExpect(jsonPath("$.recommendedCandidate.targetAmountKrw").value(500_000))
			.andExpect(jsonPath("$.intensityOptions.length()").value(3))
			.andExpect(jsonPath("$.relatedProductId").value("hana-saving-info-001"))
			.andReturn();
		String productId = response(recommendation).path("relatedProductId").asText();

		mockMvc.perform(get("/api/v1/hana-products/{productId}", productId).header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewedCatalog").value(true))
			.andExpect(jsonPath("$.inAppEnrollmentAvailable").value(false))
			.andExpect(jsonPath("$.affectsProgress").value(false));
	}

	@Test
	void personalizesSavingRecommendationFromTheUsersGoalGap() throws Exception {
		String authorization = authorization(signUp("vnext-personalized-routine@example.com"));
		completeExploreOnlyOnboarding(authorization, "vnext-personalized-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "vnext-personalized-goal-key", 2_000_000, 2_600_000)
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/routine-adaptations")
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"groupId":"group-saving-30","adventurerId":"adv-cobalt","sourceRoutineId":"routine-weekly-save","selectedDomain":"SAVING"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.intensityOptions[0].targetAmountKrw").value(60_000))
			.andExpect(jsonPath("$.recommendedCandidate.targetAmountKrw").value(100_000))
			.andExpect(jsonPath("$.intensityOptions[2].targetAmountKrw").value(140_000));
	}

	@Test
	void adventurerReportUsesTheUsersLatestSavingRate() throws Exception {
		MvcResult signup = signUp("vnext-mate-saving-rate@example.com");
		String authorization = authorization(signup);
		UUID userId = UUID.fromString(response(signup).path("user").path("userId").asText());
		completeExploreOnlyOnboarding(authorization, "vnext-mate-saving-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "vnext-mate-saving-goal").andExpect(status().isCreated());
		snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(2_000_000, 4_800, 3_450, 4_200, 0, Instant.now().plusSeconds(60)));

		mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/report")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comparisonMetrics[0].myRange").value("34.5%"));
	}

	@Test
	void routineCandidateDurationMatchesTheRemainingGoalMonths() throws Exception {
		MvcResult signup = signUp("vnext-routine-duration@example.com");
		String authorization = authorization(signup);
		UUID userId = UUID.fromString(response(signup).path("user").path("userId").asText());
		YearMonth currentMonth = YearMonth.now(ZoneId.of("Asia/Seoul"));
		completeExploreOnlyOnboarding(authorization, "vnext-routine-duration-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "vnext-routine-duration-goal", 2_000_000, 2_600_000,
			currentMonth.plusMonths(3).toString())
			.andExpect(status().isCreated());
		Instant oldConfirmation = currentMonth.minusMonths(6).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
		jdbcTemplate.update("UPDATE finmate_user_goal SET confirmed_at = ? WHERE user_id = ?",
			Timestamp.from(oldConfirmation), userId);

		mockMvc.perform(post("/api/v1/routine-adaptations")
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"groupId":"group-saving-30","adventurerId":"adv-cobalt","sourceRoutineId":"routine-weekly-save","selectedDomain":"SAVING"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recommendedCandidate.durationDays").value(90))
			.andExpect(jsonPath("$.intensityOptions[0].durationDays").value(90))
			.andExpect(jsonPath("$.intensityOptions[2].durationDays").value(90));
	}

	@Test
	void unboundMonthlyJourneyDoesNotInventFinancialActivityDetail() throws Exception {
		String authorization = activeGoalAuthorization("vnext-record@example.com");

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value("2026-07"))
			.andExpect(jsonPath("$.dayCount").value(31))
			.andExpect(jsonPath("$.nodes.length()").value(31))
			.andExpect(jsonPath("$.moneySummary.expenseKrw").value(0))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(0))
			.andExpect(jsonPath("$.nodes[8].primaryActivity").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));

		mockMvc.perform(get("/api/v1/records/2026-07-11").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activities").isEmpty())
			.andExpect(jsonPath("$.budget.remainingKrw").value(0))
			.andExpect(jsonPath("$.budget.usedBps").value(0))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
	}

	@Test
	void monthlyJourneyUsesOnlyTheUsersStoredActivity() throws Exception {
		MvcResult signup = signUp("vnext-record-override@example.com");
		String authorization = authorization(signup);
		UUID userId = UUID.fromString(response(signup).path("user").path("userId").asText());
		completeExploreOnlyOnboarding(authorization, "vnext-record-override-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "vnext-record-override-goal").andExpect(status().isCreated());
		recordService.appendDemoSaving(userId, Instant.parse("2026-07-09T03:00:00Z"), 777_000);

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nodes[8].primaryActivity.activityType").value("SAVING"))
			.andExpect(jsonPath("$.nodes[8].primaryActivity.amountKrw").value(777_000))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(777_000));
	}

	@Test
	void julyJourneyDoesNotLockADateThatHasUserActivity() throws Exception {
		MvcResult signup = signUp("vnext-record-future-override@example.com");
		String authorization = authorization(signup);
		UUID userId = UUID.fromString(response(signup).path("user").path("userId").asText());
		completeExploreOnlyOnboarding(authorization, "vnext-record-future-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "vnext-record-future-goal").andExpect(status().isCreated());
		recordService.appendDemoSaving(userId, Instant.parse("2026-07-13T03:00:00Z"), 50_000);

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nodes[12].status").value("RECORDED"))
			.andExpect(jsonPath("$.nodes[12].primaryActivity.amountKrw").value(50_000));
	}

	private String activeGoalAuthorization(String email) throws Exception {
		String authorization = authorization(signUp(email));
		completeExploreOnlyOnboarding(authorization, "vnext-onboarding-" + email.hashCode()).andExpect(status().isOk());
		confirmGoal(authorization, "vnext-confirm-goal-" + email.hashCode()).andExpect(status().isCreated());
		return authorization;
	}

	private org.springframework.test.web.servlet.ResultActions completeExploreOnlyOnboarding(String authorization,
		String idempotencyKey) throws Exception {
		return mockMvc.perform(put("/api/v1/onboarding").header("Authorization", authorization)
			.header("Idempotency-Key", padKey(idempotencyKey)).contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"displayName":"미나","context":{"incomeRegularity":"REGULAR","housingType":"RENT","fixedCostBurden":"MEDIUM"},"moneyConcern":"SAVING","financialTendency":"BALANCED","lifestyleTags":["자취","사회초년생"],"anonymousShareConsent":true,"syntheticMyDataConsent":true,"finishMode":"EXPLORE_ONLY"}
				"""));
	}

	private org.springframework.test.web.servlet.ResultActions confirmGoal(String authorization, String idempotencyKey)
		throws Exception {
		return confirmGoal(authorization, idempotencyKey, 2_000_000, 5_000_000);
	}

	private org.springframework.test.web.servlet.ResultActions confirmGoal(String authorization, String idempotencyKey,
		long currentAmountKrw, long targetAmountKrw) throws Exception {
		return confirmGoal(authorization, idempotencyKey, currentAmountKrw, targetAmountKrw, "2027-01");
	}

	private org.springframework.test.web.servlet.ResultActions confirmGoal(String authorization, String idempotencyKey,
		long currentAmountKrw, long targetAmountKrw, String targetMonth) throws Exception {
		return mockMvc.perform(post("/api/v1/goals").header("Authorization", authorization)
			.header("Idempotency-Key", padKey(idempotencyKey)).contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"goal":{"title":"유럽여행경비","domain":"SAVING","currentAmountKrw":%d,"targetAmountKrw":%d,"targetMonth":"%s"},"confirm":true}
				""".formatted(currentAmountKrw, targetAmountKrw, targetMonth)));
	}

	private String padKey(String value) {
		return value.length() >= 16 ? value : value + "-0123456789abcdef";
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
