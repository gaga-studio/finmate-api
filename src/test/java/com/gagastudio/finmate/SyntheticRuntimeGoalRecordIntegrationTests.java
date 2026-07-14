package com.gagastudio.finmate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.goals.SyntheticSnapshotIngestionService;
import com.gagastudio.finmate.goals.SyntheticSnapshotInput;
import com.gagastudio.finmate.records.RecordService;
import com.gagastudio.finmate.runtime.SyntheticRuntimeReadService;
import java.time.Instant;
import java.util.UUID;
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
class SyntheticRuntimeGoalRecordIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired RecordService recordService;
	@Autowired SyntheticSnapshotIngestionService snapshotIngestion;
	@Autowired SyntheticRuntimeReadService runtimeReads;

	@Test
	void boundActivitiesDriveBaselineHomeAndInitialGoalSnapshotWithoutTotalAssetsFixture() throws Exception {
		MvcResult signup = signUp("runtime-goal-fresh@example.com");
		UUID userId = userId(signup);
		String authorization = authorization(signup);
		seedRuntimePersona(userId, "P-RUNTIME-GOAL-FRESH", "FRESH", 3_333, 2_500, 833);
		seedFreshMetricActivities("P-RUNTIME-GOAL-FRESH");
		org.assertj.core.api.Assertions.assertThat(runtimeReads.featureProfile(userId)).get()
			.extracting("consumptionRateBps", "savingRateBps", "investmentRateBps")
			.containsExactly(3_333, 2_500, 833);

		completeExploreOnboarding(authorization, "runtime-goal-fresh-onboarding")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baseline.disposableIncomeKrw").value(1_200_000))
			.andExpect(jsonPath("$.baseline.spendingRateBps").value(3_333))
			.andExpect(jsonPath("$.baseline.savingRateBps").value(2_500))
			.andExpect(jsonPath("$.baseline.investmentJudgmentBps").value(833))
			.andExpect(jsonPath("$.dataState").value("FRESH"));

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAssetsKrw").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.spendingDefenseBps").value(3_333))
			.andExpect(jsonPath("$.financialStats.savingHpBps").value(2_500))
			.andExpect(jsonPath("$.financialStats.investmentJudgmentBps").value(833));

		confirmGoal(authorization, "runtime-goal-fresh-confirm").andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAssetsKrw").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.spendingDefenseBps").value(3_333))
			.andExpect(jsonPath("$.financialStats.savingHpBps").value(2_500))
			.andExpect(jsonPath("$.financialStats.investmentJudgmentBps").value(833));
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForMap("""
			SELECT spending_bps, saving_bps, investment_judgment_bps
			FROM finmate_synthetic_financial_snapshot WHERE user_id = ?
			""", userId))
			.containsEntry("spending_bps", 3_333)
			.containsEntry("saving_bps", 2_500)
			.containsEntry("investment_judgment_bps", 833);
	}

	@Test
	void nonPositiveDisposableIncomeKeepsFinancialMetricsNullAndDoesNotCreateASnapshot() throws Exception {
		MvcResult signup = signUp("runtime-goal-insufficient@example.com");
		UUID userId = userId(signup);
		String authorization = authorization(signup);
		seedRuntimePersona(userId, "P-RUNTIME-GOAL-INSUFFICIENT", "INSUFFICIENT", null, null, null);
		activity("insufficient-income-may", "P-RUNTIME-GOAL-INSUFFICIENT", "INCOME", "INFLOW",
			"EARNED_INCOME", "급여", 500_000, "2026-05-10T00:00:00Z");
		activity("insufficient-income-june", "P-RUNTIME-GOAL-INSUFFICIENT", "INCOME", "INFLOW",
			"EARNED_INCOME", "급여", 500_000, "2026-06-10T00:00:00Z");
		activity("insufficient-income-july", "P-RUNTIME-GOAL-INSUFFICIENT", "INCOME", "INFLOW",
			"EARNED_INCOME", "급여", 500_000, "2026-07-10T00:00:00Z");
		activity("insufficient-essential", "P-RUNTIME-GOAL-INSUFFICIENT", "SPENDING", "OUTFLOW",
			"ESSENTIAL_EXPENSE", "주거", 600_000, "2026-07-11T00:00:00Z");

		completeExploreOnboarding(authorization, "runtime-goal-insufficient-onboarding")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.baseline.disposableIncomeKrw").value(nullValue()))
			.andExpect(jsonPath("$.baseline.spendingRateBps").value(nullValue()))
			.andExpect(jsonPath("$.baseline.savingRateBps").value(nullValue()))
			.andExpect(jsonPath("$.baseline.investmentJudgmentBps").value(nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
		confirmGoal(authorization, "runtime-goal-insufficient-confirm").andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/home").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAssetsKrw").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.spendingDefenseBps").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.savingHpBps").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.investmentJudgmentBps").value(nullValue()))
			.andExpect(jsonPath("$.financialStats.questXp").value(0))
			.andExpect(jsonPath("$.raid.dataState").value("INSUFFICIENT"))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
		org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM finmate_synthetic_financial_snapshot WHERE user_id = ?", Long.class, userId)).isZero();
	}

	@Test
	void characterTrendUsesActualMonthlySnapshotsInsteadOfSubtractingThreeHundred() throws Exception {
		MvcResult signup = signUp("runtime-character-trend@example.com");
		UUID userId = userId(signup);
		String authorization = authorization(signup);
		seedRuntimePersona(userId, "P-RUNTIME-CHARACTER", "FRESH", 3_333, 2_500, 833);
		seedFreshMetricActivities("P-RUNTIME-CHARACTER");
		completeExploreOnboarding(authorization, "runtime-character-onboarding").andExpect(status().isOk());
		confirmGoal(authorization, "runtime-character-confirm").andExpect(status().isCreated());
		snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(2_200_000, 3_200, 2_700, 900, 999, Instant.parse("2026-08-15T00:00:00Z")));
		snapshotIngestion.ingest(userId,
			new SyntheticSnapshotInput(2_400_000, 3_100, 3_100, 950, 999, Instant.parse("2026-09-15T00:00:00Z")));

		mockMvc.perform(get("/api/v1/reports/characters/SAVING_HP").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scoreBps").value(3_100))
			.andExpect(jsonPath("$.financialStats").doesNotExist())
			.andExpect(jsonPath("$.trend30Days[*].value", contains(2_700, 3_100)))
			.andExpect(jsonPath("$.trend30Days[*].date", contains("2026-08-15", "2026-09-15")));
	}

	@Test
	void recordsCombineBoundFinancialActivityAndAppEventsUsingSeoulDatesAndAbsolutePrimaryAmount() throws Exception {
		MvcResult signup = signUp("runtime-records@example.com");
		UUID userId = userId(signup);
		String authorization = authorization(signup);
		seedRuntimePersona(userId, "P-RUNTIME-RECORD", "FRESH", 4_000, 2_000, 500);
		activity("record-income", "P-RUNTIME-RECORD", "INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_000_000,
			"2025-10-31T15:30:00Z");
		activity("record-expense-one", "P-RUNTIME-RECORD", "SPENDING", "OUTFLOW", "ESSENTIAL_EXPENSE", "주거",
			300_000, "2025-10-31T16:00:00Z");
		activity("record-saving", "P-RUNTIME-RECORD", "SAVING", "OUTFLOW", "SAVING_CONTRIBUTION", "비상금",
			100_000, "2025-11-01T00:00:00Z");
		activity("record-investment", "P-RUNTIME-RECORD", "INVESTMENT", "OUTFLOW", "BROKERAGE_TRANSFER", "ETF",
			50_000, "2025-11-01T01:00:00Z");
		activity("record-expense-two", "P-RUNTIME-RECORD", "SPENDING", "OUTFLOW", "DISCRETIONARY_EXPENSE", "여가",
			500_000, "2025-11-15T01:00:00Z");
		recordService.appendQuestCompletion(userId, Instant.parse("2025-11-15T02:00:00Z"), "예산 퀘스트", 20);
		recordService.appendSyntheticRecalculation(userId, Instant.parse("2025-11-15T03:00:00Z"));
		mockMvc.perform(put("/api/v1/records/2025-11-15").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reflection\":\"이번 달 지출을 점검했다.\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2025-11"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.moneySummary.incomeKrw").value(2_000_000))
			.andExpect(jsonPath("$.moneySummary.expenseKrw").value(800_000))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(100_000))
			.andExpect(jsonPath("$.nodes[0].date").value("2025-11-01"))
			.andExpect(jsonPath("$.nodes[0].primaryActivity.activityType").value("INCOME"))
			.andExpect(jsonPath("$.nodes[0].primaryActivity.amountKrw").value(2_000_000))
			.andExpect(jsonPath("$.nodes[14].primaryActivity.activityType").value("EXPENSE"))
			.andExpect(jsonPath("$.nodes[14].primaryActivity.amountKrw").value(-500_000))
			.andExpect(jsonPath("$.dataState").value("FRESH"));

		mockMvc.perform(get("/api/v1/records/2025-11-15").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activities[*].activityType", contains("EXPENSE", "QUEST", "MYDATA_RECALCULATION")))
			.andExpect(jsonPath("$.activities[0].primary").value(true))
			.andExpect(jsonPath("$.activities[1].primary").value(false))
			.andExpect(jsonPath("$.xpEarned").value(20))
			.andExpect(jsonPath("$.reflection").value("이번 달 지출을 점검했다."));
	}

	@Test
	void unboundRecordMonthIsInsufficientAndDoesNotEmitTheFixedJulyFixture() throws Exception {
		String authorization = authorization(signUp("runtime-records-unbound@example.com"));

		mockMvc.perform(get("/api/v1/records/journey").header("Authorization", authorization)
				.queryParam("month", "2026-07"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recordedDayCount").value(0))
			.andExpect(jsonPath("$.moneySummary.incomeKrw").value(0))
			.andExpect(jsonPath("$.moneySummary.expenseKrw").value(0))
			.andExpect(jsonPath("$.moneySummary.savingKrw").value(0))
			.andExpect(jsonPath("$.nodes[10].primaryActivity").value(nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
		mockMvc.perform(get("/api/v1/records/2026-07-11").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activities").isEmpty())
			.andExpect(jsonPath("$.reflection").value(nullValue()))
			.andExpect(jsonPath("$.dataState").value("INSUFFICIENT"));
	}

	private void seedFreshMetricActivities(String personaId) {
		activity(personaId + "-income-may", personaId, "INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_000_000,
			"2026-05-10T00:00:00Z");
		activity(personaId + "-income-june", personaId, "INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_000_000,
			"2026-06-10T00:00:00Z");
		activity(personaId + "-income-july", personaId, "INCOME", "INFLOW", "EARNED_INCOME", "급여", 2_000_000,
			"2026-07-10T00:00:00Z");
		activity(personaId + "-essential", personaId, "SPENDING", "OUTFLOW", "ESSENTIAL_EXPENSE", "주거", 800_000,
			"2026-07-11T00:00:00Z");
		activity(personaId + "-discretionary", personaId, "SPENDING", "OUTFLOW", "DISCRETIONARY_EXPENSE", "여가",
			400_000, "2026-07-12T00:00:00Z");
		activity(personaId + "-saving", personaId, "SAVING", "OUTFLOW", "SAVING_CONTRIBUTION", "비상금", 300_000,
			"2026-07-13T00:00:00Z");
		activity(personaId + "-investment", personaId, "INVESTMENT", "OUTFLOW", "BROKERAGE_TRANSFER", "ETF", 100_000,
			"2026-07-13T01:00:00Z");
	}

	private void seedRuntimePersona(UUID userId, String personaId, String dataState, Integer consumptionRateBps,
		Integer savingRateBps, Integer investRateBps) {
		jdbcTemplate.update("""
			INSERT INTO finmate_import_persona
				(source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
				 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
				 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
				 joined_at, source_data_range, source_data_as_of, synthetic)
			VALUES (?, 'v1.0.0', '25-29', '20s', 'starter', 'EARLY_CAREER', 2500000, 'REGULAR', 2000, 1000,
				3, 'BALANCED', 'RENT', '[]', 'SAVE', 'SAVING', DATE '2026-01-01', '2026-01~2026-07',
				DATE '2026-07-13', TRUE)
			""", personaId);
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_persona
				(source_persona_id, release_version, projection_version, age_band, cohort, occupation_group,
				 income_band, spending_tendency, saving_rate_band, investment_tendency, income_regularity,
				 household_type, lifestyle_tags, money_worry, peer_discovery_opt_in, data_state, last_synced_at,
				 visible_fields, exact_values)
			VALUES (?, 'v1.0.0', 'synthetic-runtime-v1', 'AGE_24_29', '20s', 'EARLY_CAREER', 'FROM_200_TO_300',
				'BALANCED', 'FROM_10_TO_20', 'BALANCED', 'REGULAR', 'RENT', '[]', 'SAVING', TRUE, ?,
				TIMESTAMPTZ '2026-07-13 00:00:00Z', '[]', FALSE)
			""", personaId, dataState);
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_feature_profile
				(source_persona_id, release_version, projection_version, feature_month, age, cohort,
				 consumption_rate_bps, saving_rate_bps, invest_rate_bps)
			VALUES (?, 'v1.0.0', 'synthetic-runtime-v1', DATE '2026-07-01', 27, '20s', ?, ?, ?)
			""", personaId, consumptionRateBps, savingRateBps, investRateBps);
		jdbcTemplate.update("""
			INSERT INTO finmate_user_synthetic_persona_binding
				(user_id, source_persona_id, release_version, projection_version, bound_at)
			VALUES (?, ?, 'v1.0.0', 'synthetic-runtime-v1', CURRENT_TIMESTAMP)
			""", userId, personaId);
	}

	private void activity(String transactionId, String personaId, String activityType, String direction,
		String classification, String displayLabel, long amountKrw, String occurredAt) {
		jdbcTemplate.update("""
			INSERT INTO finmate_financial_activity
				(source_transaction_id, source_persona_id, release_version, activity_type, direction, classification,
				 category, subcategory, display_label, amount_krw, currency, occurred_at)
			VALUES (?, ?, 'v1.0.0', ?, ?, ?, ?, ?, ?, ?, 'KRW', ?::timestamptz)
			""", transactionId, personaId, activityType, direction, classification, displayLabel, displayLabel,
			displayLabel, amountKrw, occurredAt);
	}

	private org.springframework.test.web.servlet.ResultActions completeExploreOnboarding(String authorization, String key)
		throws Exception {
		return mockMvc.perform(put("/api/v1/onboarding").header("Authorization", authorization)
			.header("Idempotency-Key", padKey(key)).contentType(MediaType.APPLICATION_JSON).content("""
				{"displayName":"미나","context":{"incomeRegularity":"REGULAR","housingType":"RENT","fixedCostBurden":"MEDIUM"},"moneyConcern":"SAVING","financialTendency":"BALANCED","lifestyleTags":["자취"],"anonymousShareConsent":true,"syntheticMyDataConsent":true,"finishMode":"EXPLORE_ONLY"}
				"""));
	}

	private org.springframework.test.web.servlet.ResultActions confirmGoal(String authorization, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/goals").header("Authorization", authorization)
			.header("Idempotency-Key", padKey(key)).contentType(MediaType.APPLICATION_JSON).content("""
				{"goal":{"title":"유럽여행경비","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirm":true}
				"""));
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}".formatted(email)))
			.andExpect(status().isCreated()).andReturn();
	}

	private String authorization(MvcResult signup) throws Exception {
		return "Bearer " + response(signup).path("accessToken").asText();
	}

	private UUID userId(MvcResult signup) throws Exception {
		return UUID.fromString(response(signup).path("user").path("userId").asText());
	}

	private JsonNode response(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String padKey(String key) {
		return key.length() >= 16 ? key : key + "-0000000000000000";
	}
}
