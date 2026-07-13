package com.gagastudio.finmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DisclosurePublicProfileIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;

	@Test
	void exactValueDisclosureIsPrivateByDefaultPreviewedThenExplicitlyConfirmed() throws Exception {
		String authorization = authorization(signUp("disclosure@example.com"));

		mockMvc.perform(get("/api/v1/me/disclosures").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("OPTED_OUT"))
			.andExpect(jsonPath("$.exactValues").value(false))
			.andExpect(jsonPath("$.fields").isEmpty());

		String request = """
			{"fields":["ASSETS","SAVING","FINANCIAL_PRODUCTS","INVESTMENT_HOLDINGS","TRADES"],
			 "consentVersion":"financial-disclosure-v1.0","confirmExactValues":true}
			""";
		mockMvc.perform(post("/api/v1/me/disclosures/preview").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exactValues").value(true))
			.andExpect(jsonPath("$.fields.length()").value(5))
			.andExpect(jsonPath("$.permanentlyExcludedFields[0]").value("ACCOUNT_NUMBER"));

		mockMvc.perform(put("/api/v1/me/disclosures").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("ACTIVE"))
			.andExpect(jsonPath("$.exactValues").value(true))
			.andExpect(jsonPath("$.version").value(2))
			.andExpect(jsonPath("$.fields[0]").value("ASSETS"));

		mockMvc.perform(get("/api/v1/me").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.privacy.anonymousCardOptIn").value(true))
			.andExpect(jsonPath("$.privacy.exposedFields.length()").value(5));
	}

	@Test
	void withdrawalImmediatelyClearsEveryExactValueDisclosure() throws Exception {
		String authorization = authorization(signUp("withdraw@example.com"));
		String request = """
			{"fields":["ASSETS","INCOME","SPENDING"],
			 "consentVersion":"financial-disclosure-v1.0","confirmExactValues":true}
			""";
		mockMvc.perform(put("/api/v1/me/disclosures").header("Authorization", authorization)
			.contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/me/disclosures").header("Authorization", authorization))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/disclosures").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("OPTED_OUT"))
			.andExpect(jsonPath("$.fields").isEmpty())
			.andExpect(jsonPath("$.version").value(3));
	}

	@Test
	void approvedSyntheticAdventurerExposesOnlyConsentedExactFinancialSections() throws Exception {
		String authorization = authorization(signUp("public-profile@example.com"));

		MvcResult result = mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/financial-profile")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.synthetic").value(true))
			.andExpect(jsonPath("$.exactValues").value(true))
			.andExpect(jsonPath("$.assets[0].balanceKrw").value(5000000))
			.andExpect(jsonPath("$.income[0].amountKrw").value(2800000))
			.andExpect(jsonPath("$.spending[0].amountKrw").value(42000))
			.andExpect(jsonPath("$.savings[0].amountKrw").value(500000))
			.andExpect(jsonPath("$.products[0].productName").value("하나 여행 적금"))
			.andExpect(jsonPath("$.investments[0].ticker").value("069500.KS"))
			.andExpect(jsonPath("$.trades[0].action").value("BUY"))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		for (String forbidden : new String[] {"accountNumber", "rawMemo", "employer", "location", "apiRef"}) {
			assertThat(body).doesNotContain("\"" + forbidden + "\"");
		}
	}

	@Test
	void publicFinancialItemsCannotStoreMissingExactValues() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE finmate_public_financial_item
			SET balance_krw = NULL
			WHERE id = '00000000-0000-0000-0000-000000000201'
			"""))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void nonConsentedFinancialSectionsAreOmittedAndPublicProfileHasNoWriteRoute() throws Exception {
		String authorization = authorization(signUp("limited-profile@example.com"));

		mockMvc.perform(get("/api/v1/mate/groups/group-demo-10/adventurers/adv-demo/financial-profile")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.visibleFields.length()").value(2))
			.andExpect(jsonPath("$.assets[0]").exists())
			.andExpect(jsonPath("$.savings[0]").exists())
			.andExpect(jsonPath("$.products").doesNotExist())
			.andExpect(jsonPath("$.investments").doesNotExist())
			.andExpect(jsonPath("$.trades").doesNotExist());

		mockMvc.perform(post("/api/v1/mate/groups/group-demo-10/adventurers/adv-demo/financial-profile")
				.header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void exactValueConfirmationCannotBeSkipped() throws Exception {
		String authorization = authorization(signUp("confirmation@example.com"));
		mockMvc.perform(put("/api/v1/me/disclosures").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"fields":["ASSETS"],"consentVersion":"financial-disclosure-v1.0","confirmExactValues":false}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void disclosureConsentVersionIsRequired() throws Exception {
		String authorization = authorization(signUp("missing-version@example.com"));
		mockMvc.perform(put("/api/v1/me/disclosures").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"fields":["ASSETS"],"confirmExactValues":true}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void withdrawnSyntheticProfileDisappearsFromRecommendationsAndDetailImmediately() throws Exception {
		String authorization = authorization(signUp("revoked-profile@example.com"));

		jdbcTemplate.update("UPDATE finmate_synthetic_public_profile SET consent_state = 'OPTED_OUT' WHERE source_persona_id = 'P-CB-001'");
		try {
			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.adventurerId == 'adv-cobalt')]").isEmpty());
			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/financial-profile")
					.header("Authorization", authorization))
				.andExpect(status().isNotFound());
			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/routines/routine-weekly-save")
					.header("Authorization", authorization))
				.andExpect(status().isNotFound());
			mockMvc.perform(post("/api/v1/routine-adaptations").header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"groupId":"group-saving-30","adventurerId":"adv-cobalt","routineId":"routine-weekly-save"}
						"""))
				.andExpect(status().isNotFound());
		} finally {
			jdbcTemplate.update("UPDATE finmate_synthetic_public_profile SET consent_state = 'ACTIVE' WHERE source_persona_id = 'P-CB-001'");
		}
	}

	@Test
	void ownerDisclosureUpdateAndWithdrawalImmediatelyProjectToMateDiscovery() throws Exception {
		MvcResult signup = signUp("profile-owner@example.com");
		String authorization = authorization(signup);
		String userId = response(signup).path("user").path("userId").asText();

		jdbcTemplate.update("""
			UPDATE finmate_synthetic_public_profile
			SET owner_user_id = ?::uuid, consent_state = 'OPTED_OUT'
			WHERE source_persona_id = 'P-CB-001'
			""", userId);
		try {
			mockMvc.perform(put("/api/v1/me/disclosures").header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"fields":["ASSETS"],"consentVersion":"financial-disclosure-v1.0","confirmExactValues":true}
						"""))
				.andExpect(status().isOk());

			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/financial-profile")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibleFields.length()").value(1))
				.andExpect(jsonPath("$.visibleFields[0]").value("ASSETS"))
				.andExpect(jsonPath("$.assets[0]").exists())
				.andExpect(jsonPath("$.income").doesNotExist());

			mockMvc.perform(delete("/api/v1/me/disclosures").header("Authorization", authorization))
				.andExpect(status().isNoContent());
			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers")
					.header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.adventurerId == 'adv-cobalt')]").isEmpty());
			mockMvc.perform(get("/api/v1/mate/groups/group-saving-30/adventurers/adv-cobalt/financial-profile")
					.header("Authorization", authorization))
				.andExpect(status().isNotFound());
		} finally {
			jdbcTemplate.update("""
				UPDATE finmate_synthetic_public_profile
				SET owner_user_id = NULL,
					visible_fields = '["ASSETS","INCOME","SPENDING","SAVING","FINANCIAL_PRODUCTS","INVESTMENT_HOLDINGS","TRADES"]',
					exact_values = TRUE, consent_state = 'ACTIVE', consent_version = 'financial-disclosure-v1.0'
				WHERE source_persona_id = 'P-CB-001'
				""");
		}
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
