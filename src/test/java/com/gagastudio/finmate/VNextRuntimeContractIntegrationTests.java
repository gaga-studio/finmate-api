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
			.andExpect(jsonPath("$.baseline.disposableIncomeKrw").value(1_100_000))
			.andExpect(jsonPath("$.mainGoal").doesNotExist());

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("EXPLORE_ONLY"))
			.andExpect(jsonPath("$.mainGoal").doesNotExist())
			.andExpect(jsonPath("$.raid").doesNotExist())
			.andExpect(jsonPath("$.financialStats.savingHpBps").value(1_800))
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
			.andExpect(jsonPath("$.scoreBps").value(1_800))
			.andExpect(jsonPath("$.metrics").isNotEmpty())
			.andExpect(jsonPath("$.trend30Days.length()").value(2));
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
			.andExpect(jsonPath("$.items[0].adventurerId").value("adv-cobalt"));

		mockMvc.perform(post("/api/v1/mate/explore/search").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"ageBand":"AGE_30_34","occupationGroup":"FREELANCER","incomeBand":"OVER_300","spendingTendency":"VARIABLE","savingRateBand":"UNDER_10","investmentTendency":"LEARNING"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());

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
	void returnsMonthlyJourneyAndCompleteDailyActivityDetail() throws Exception {
		String authorization = activeGoalAuthorization("vnext-record@example.com");

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value("2026-07"))
			.andExpect(jsonPath("$.dayCount").value(31))
			.andExpect(jsonPath("$.nodes.length()").value(31))
			.andExpect(jsonPath("$.moneySummary.expenseKrw").value(163_400))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(100_000))
			.andExpect(jsonPath("$.nodes[8].primaryActivity.title").value("장보기"))
			.andExpect(jsonPath("$.nodes[10].primaryActivity.activityType").value("INCOME"));

		mockMvc.perform(get("/api/v1/records/2026-07-11").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("TODAY"))
			.andExpect(jsonPath("$.activities.length()").value(5))
			.andExpect(jsonPath("$.activities[0].activityType").value("INCOME"))
			.andExpect(jsonPath("$.budget.remainingKrw").value(12_400))
			.andExpect(jsonPath("$.budget.usedBps").value(6_125));
	}

	@Test
	void monthlyJourneyUsesUserActivityInsteadOfTheJulyFallbackForThatDate() throws Exception {
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
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(877_000));
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
