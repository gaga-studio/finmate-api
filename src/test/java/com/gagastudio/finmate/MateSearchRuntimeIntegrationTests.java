package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class MateSearchRuntimeIntegrationTests {
	private static final String EXACT_REQUEST = """
		{"ageBand":"AGE_24_29","occupationGroup":"EARLY_CAREER","incomeBand":"FROM_200_TO_300","spendingTendency":"BALANCED","savingRateBand":"FROM_10_TO_20","investmentTendency":"BALANCED"}
		""";

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@AfterEach
	void removeSearchFixtures() {
		jdbcTemplate.update("DELETE FROM finmate_user_synthetic_persona_binding WHERE source_persona_id LIKE 'MS-%'");
		jdbcTemplate.update("DELETE FROM finmate_import_persona WHERE source_persona_id LIKE 'MS-%'");
	}

	@Test
	void returnsTheTopSixExactRuntimeMatches() throws Exception {
		Session caller = signUp();
		for (int index = 1; index <= 7; index++) {
			seedPersona("MS-EXACT-0" + index, exactPersona(index));
		}

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(6))
			.andExpect(jsonPath("$.totalEligible").value(7))
			.andExpect(jsonPath("$.matchMode").value("EXACT"))
			.andExpect(jsonPath("$.relaxedFilters").isEmpty())
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-EXACT-07"))
			.andExpect(jsonPath("$.items[0].groupId").value("synthetic-runtime"))
			.andExpect(jsonPath("$.items[0].sourceGroupId").value("cluster-11"))
			.andExpect(jsonPath("$.items[0].similarityScoreBps").value(10_000))
			.andExpect(jsonPath("$.items[0].maintenanceDays").value(210))
			.andExpect(jsonPath("$.items[0].representativeRoutine.routineId").value("automatic_saving"))
			.andExpect(jsonPath("$.items[0].matchedFilters.length()").value(6))
			.andExpect(jsonPath("$.items[0].dataAsOf").value("2026-07-01"))
			.andExpect(jsonPath("$.items[0].assets").doesNotExist())
			.andExpect(jsonPath("$.items[0].income").doesNotExist())
			.andExpect(jsonPath("$.items[0].stocks").doesNotExist())
			.andExpect(jsonPath("$.items[5].adventurerId").value("MS-EXACT-02"))
			.andExpect(jsonPath("$.calculationVersion").value("mate-search-runtime-v1"))
			.andExpect(jsonPath("$.dataState").value("FRESH"))
			.andExpect(jsonPath("$.lastSyncedAt").value("2026-07-13T00:00:00Z"));
	}

	@Test
	void returnsSixAfterCumulativeRelaxationInTheApprovedOrder() throws Exception {
		Session caller = signUp();
		seedPersona("MS-RELAX-EXACT", exactPersona(2));
		seedPersona("MS-RELAX-AGE", persona("AGE_30_34", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 2));
		seedPersona("MS-RELAX-OCC", persona("AGE_24_29", "FREELANCER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 2));
		seedPersona("MS-RELAX-SPEND", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"VARIABLE", "FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 2));
		seedPersona("MS-RELAX-INV-A", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "LEARNING", true, "FRESH", "automatic_saving", "SAVING", 2));
		seedPersona("MS-RELAX-INV-B", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "CAUTIOUS", true, "FRESH", "automatic_saving", "SAVING", 2));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(6))
			.andExpect(jsonPath("$.totalEligible").value(6))
			.andExpect(jsonPath("$.matchMode").value("RELAXED"))
			.andExpect(jsonPath("$.relaxedFilters[0]").value("ageBand"))
			.andExpect(jsonPath("$.relaxedFilters[1]").value("occupationGroup"))
			.andExpect(jsonPath("$.relaxedFilters[2]").value("spendingTendency"))
			.andExpect(jsonPath("$.relaxedFilters[3]").value("investmentTendency"))
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-RELAX-EXACT"))
			.andExpect(jsonPath("$.items[0].similarityScoreBps").value(10_000))
			.andExpect(jsonPath("$.items[1].adventurerId").value("MS-RELAX-INV-A"))
			.andExpect(jsonPath("$.items[1].similarityScoreBps").value(9_000))
			.andExpect(jsonPath("$.items[1].matchedFilters.length()").value(5))
			.andExpect(jsonPath("$.items[2].adventurerId").value("MS-RELAX-INV-B"))
			.andExpect(jsonPath("$.items[3].adventurerId").value("MS-RELAX-AGE"));
	}

	@Test
	void returnsFewerThanSixWhenOnlyExactMatchesAreEligible() throws Exception {
		Session caller = signUp();
		seedPersona("MS-FEWER-01", exactPersona(1));
		seedPersona("MS-FEWER-02", exactPersona(2));
		seedPersona("MS-FEWER-03", exactPersona(3));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.totalEligible").value(3))
			.andExpect(jsonPath("$.matchMode").value("EXACT"))
			.andExpect(jsonPath("$.relaxedFilters").isEmpty())
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-FEWER-03"));
	}

	@Test
	void returnsNoneWithoutRelaxingIncomeOrSaving() throws Exception {
		Session caller = signUp();
		seedPersona("MS-NONE-INCOME", persona("AGE_24_29", "EARLY_CAREER", "OVER_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 4));
		seedPersona("MS-NONE-SAVING", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "OVER_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 4));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalEligible").value(0))
			.andExpect(jsonPath("$.matchMode").value("NONE"))
			.andExpect(jsonPath("$.relaxedFilters").isEmpty())
			.andExpect(jsonPath("$.lastSyncedAt").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void ordersCompleteTiesByAdventurerId() throws Exception {
		Session caller = signUp();
		seedPersona("MS-TIE-C", exactPersona(2));
		seedPersona("MS-TIE-A", exactPersona(2));
		seedPersona("MS-TIE-B", exactPersona(2));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-TIE-A"))
			.andExpect(jsonPath("$.items[1].adventurerId").value("MS-TIE-B"))
			.andExpect(jsonPath("$.items[2].adventurerId").value("MS-TIE-C"));
	}

	@Test
	void excludesSelfPrivateInsufficientAndUnsafeRuntimePersonas() throws Exception {
		Session caller = signUp();
		seedPersona("MS-KEEP", exactPersona(2));
		seedPersona("MS-SELF", exactPersona(20));
		bind(caller.userId(), "MS-SELF");
		seedPersona("MS-PRIVATE", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", false, "FRESH", "automatic_saving", "SAVING", 20));
		seedPersona("MS-INSUFFICIENT-STATE", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "INSUFFICIENT", "automatic_saving", "SAVING", 20));
		seedPersona("MS-INSUFFICIENT-ROUTINE", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", 0));
		seedPersona("MS-UNSAFE", persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300",
			"BALANCED", "FROM_10_TO_20", "BALANCED", true, "FRESH", "day_trading", "SAVING", 20));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalEligible").value(1))
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-KEEP"));
	}

	@Test
	void runtimeSearchResultLinksOpenCompatibleDetailsAndRoutine() throws Exception {
		Session caller = signUp();
		seedPersona("MS-DETAIL", exactPersona(3));

		mockMvc.perform(post("/api/v1/mate/explore/search")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content(EXACT_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].groupId").value("synthetic-runtime"))
			.andExpect(jsonPath("$.items[0].adventurerId").value("MS-DETAIL"));

		mockMvc.perform(get("/api/v1/mate/groups/synthetic-runtime/adventurers/MS-DETAIL")
				.header("Authorization", caller.authorization()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.adventurerId").value("MS-DETAIL"))
			.andExpect(jsonPath("$.groupId").value("synthetic-runtime"))
			.andExpect(jsonPath("$.routines[0].routineId").value("automatic_saving"))
			.andExpect(jsonPath("$.routines[0].maintenanceDays").value(90));

		mockMvc.perform(get("/api/v1/mate/groups/synthetic-runtime/adventurers/MS-DETAIL/routines/automatic_saving")
				.header("Authorization", caller.authorization()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routineId").value("automatic_saving"))
			.andExpect(jsonPath("$.adventurerId").value("MS-DETAIL"))
			.andExpect(jsonPath("$.maintenanceDays").value(90));
	}

	@Test
	void runtimeSearchRoutineCanBeAdaptedWithoutLegacyFixtures() throws Exception {
		Session caller = signUp();
		completeOnboarding(caller.authorization());
		seedPersona("MS-ADAPT", exactPersona(4));

		mockMvc.perform(post("/api/v1/routine-adaptations")
				.header("Authorization", caller.authorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"groupId":"synthetic-runtime","adventurerId":"MS-ADAPT","sourceRoutineId":"automatic_saving","selectedDomain":"SAVING"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourceRoutineId").value("automatic_saving"))
			.andExpect(jsonPath("$.selectedDomain").value("SAVING"))
			.andExpect(jsonPath("$.recommendedCandidate.difficulty").value("STANDARD"));
	}

	private Persona exactPersona(int maintainedMonths) {
		return persona("AGE_24_29", "EARLY_CAREER", "FROM_200_TO_300", "BALANCED",
			"FROM_10_TO_20", "BALANCED", true, "FRESH", "automatic_saving", "SAVING", maintainedMonths);
	}

	private Persona persona(String ageBand, String occupationGroup, String incomeBand,
		String spendingTendency, String savingRateBand, String investmentTendency,
		boolean peerDiscoveryOptIn, String dataState, String routine, String routineDomain,
		int maintainedMonths) {
		return new Persona(ageBand, occupationGroup, incomeBand, spendingTendency, savingRateBand,
			investmentTendency, peerDiscoveryOptIn, dataState, routine, routineDomain, maintainedMonths);
	}

	private void bind(UUID userId, String sourcePersonaId) {
		jdbcTemplate.update("""
			INSERT INTO finmate_user_synthetic_persona_binding
				(user_id, source_persona_id, release_version, bound_at)
			VALUES (?, ?, 'v1.0.0', TIMESTAMPTZ '2026-07-13 00:00:00Z')
			""", userId, sourcePersonaId);
	}

	private void seedPersona(String id, Persona persona) {
		jdbcTemplate.update("""
			INSERT INTO finmate_import_persona
				(source_persona_id, release_version, age_band, cohort, archetype, occupation_group,
				 monthly_income_krw, income_regularity, target_saving_rate_bps, target_investment_rate_bps,
				 risk_score, risk_attitude, household_type, lifestyle_tags, financial_goal, money_worry,
				 joined_at, source_data_range, source_data_as_of, synthetic)
			VALUES (?, 'v1.0.0', '25-29', '20s', 'starter', ?, 2500000, 'REGULAR', 1800, 1000,
				3, 'BALANCED', 'RENT', '[]', 'SAVE', 'SAVING', DATE '2026-01-01',
				'2026-01~2026-07', DATE '2026-07-13', TRUE)
			""", id, persona.occupationGroup());
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_persona
				(source_persona_id, release_version, age_band, cohort, occupation_group, income_band,
				 spending_tendency, saving_rate_band, investment_tendency, income_regularity, household_type,
				 lifestyle_tags, money_worry, peer_discovery_opt_in, data_state, last_synced_at)
			VALUES (?, 'v1.0.0', ?, '20s', ?, ?, ?, ?, ?, 'REGULAR', 'RENT',
				'["자취","여행"]', 'SAVING', ?, ?, TIMESTAMPTZ '2026-07-13 00:00:00Z')
			""", id, persona.ageBand(), persona.occupationGroup(), persona.incomeBand(),
			persona.spendingTendency(), persona.savingRateBand(), persona.investmentTendency(),
			persona.peerDiscoveryOptIn(), persona.dataState());
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_feature_profile
				(source_persona_id, release_version, feature_month, lifestyle_cluster_id)
			VALUES (?, 'v1.0.0', DATE '2026-07-01', '11')
			""", id);
		jdbcTemplate.update("""
			INSERT INTO finmate_synthetic_runtime_routine
				(source_persona_id, release_version, source_routine, domain, frequency, maintained_months)
			VALUES (?, 'v1.0.0', ?, ?, 'MONTHLY', ?)
			""", id, persona.routine(), persona.routineDomain(), persona.maintainedMonths());
	}

	private Session signUp() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"mate-search-%s@example.com\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}"
					.formatted(UUID.randomUUID())))
			.andExpect(status().isCreated()).andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return new Session("Bearer " + response.path("accessToken").asText(),
			UUID.fromString(response.path("user").path("userId").asText()));
	}

	private void completeOnboarding(String authorization) throws Exception {
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", authorization)
				.header("Idempotency-Key", "mate-search-onboarding-key-0001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"미나","mainGoal":{"title":"유럽여행경비","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
					"""))
			.andExpect(status().isOk());
	}

	private record Session(String authorization, UUID userId) {
	}

	private record Persona(String ageBand, String occupationGroup, String incomeBand,
		String spendingTendency, String savingRateBand, String investmentTendency,
		boolean peerDiscoveryOptIn, String dataState, String routine, String routineDomain,
		int maintainedMonths) {
	}
}
