package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
class AuthOnboardingIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;

	@Test
	void signupCreatesAnAuthenticatedSessionForANormalizedEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"MinJi.Kim@Example.COM","password":"FinMate!2026#","displayName":"Minji"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(cookie().exists("finmate_refresh"))
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.user.email").value("minji.kim@example.com"));
	}

	@Test
	void duplicateSignupReturnsAConflictProblem() throws Exception {
		signUp("duplicate@example.com");

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpBody("duplicate@example.com")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
	}

	@Test
	void loginWithWrongPasswordReturnsAnUnauthorizedProblem() throws Exception {
		signUp("login-failure@example.com");

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login-failure@example.com\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void authenticatedUserCanReadDefaultProfile() throws Exception {
		MvcResult signup = signUp("me@example.com");

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken(signup)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value("me@example.com"))
			.andExpect(jsonPath("$.preferences.raidMotion").value("REDUCED"))
			.andExpect(jsonPath("$.preferences.pushEnabled").value(false))
			.andExpect(jsonPath("$.preferences.locale").value("ko-KR"))
			.andExpect(jsonPath("$.preferences.timeZone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.privacy.anonymousCardOptIn").value(false))
			.andExpect(jsonPath("$.privacy.exposedFields").isEmpty());
	}

	@Test
	void missingBearerTokenReturnsAnUnauthorizedProblem() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void authenticatedUserCanSaveOnboarding() throws Exception {
		MvcResult signup = signUp("onboarding@example.com");

		mockMvc.perform(put("/api/v1/me/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.contentType(MediaType.APPLICATION_JSON)
				.content(onboardingBody()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.onboardingStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.onboarding.housingType").value("MONTHLY_RENT"));
	}

	@Test
	void onboardingRejectsMissingRequiredFieldsWithAProblemDetail() throws Exception {
		MvcResult signup = signUp("onboarding-validation@example.com");

		mockMvc.perform(put("/api/v1/me/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"employmentType":"EMPLOYEE","incomeRegularity":"REGULAR","hasDependents":false,"primaryConcern":"SAVING","changePace":"BALANCED","riskTolerance":"CONSERVATIVE","notificationPreference":"IMPORTANT_ONLY","contextTags":["newcomer"],"profileConsentVersion":"profile-consent-v1.0"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void authenticatedUserCanSavePreferences() throws Exception {
		MvcResult signup = signUp("preferences@example.com");

		mockMvc.perform(put("/api/v1/me/preferences")
				.header("Authorization", "Bearer " + accessToken(signup))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"raidMotion\":\"OFF\",\"pushEnabled\":true,\"locale\":\"ko-KR\",\"timeZone\":\"Asia/Seoul\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.raidMotion").value("OFF"))
			.andExpect(jsonPath("$.pushEnabled").value(true));
	}

	@Test
	void refreshRotatesTheOpaqueRefreshCookie() throws Exception {
		MvcResult signup = signUp("refresh@example.com");
		String firstRefresh = refreshToken(signup);

		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("finmate_refresh", firstRefresh)))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("finmate_refresh"))
			.andReturn();

		org.assertj.core.api.Assertions.assertThat(refreshToken(refreshed)).isNotEqualTo(firstRefresh);
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("finmate_refresh", firstRefresh)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesTheRefreshCookie() throws Exception {
		MvcResult signup = signUp("logout@example.com");
		String refresh = refreshToken(signup);

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + accessToken(signup))
				.cookie(new Cookie("finmate_refresh", refresh)))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("finmate_refresh", 0));

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("finmate_refresh", refresh)))
			.andExpect(status().isUnauthorized());
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpBody(email)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private String signUpBody(String email) {
		return "{\"email\":\"%s\",\"password\":\"FinMate!2026#\",\"displayName\":\"Minji\"}".formatted(email);
	}

	private String accessToken(MvcResult result) throws Exception {
		return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString())
			.path("accessToken").asText();
	}

	private String refreshToken(MvcResult result) {
		String header = result.getResponse().getHeader("Set-Cookie");
		return header.substring("finmate_refresh=".length(), header.indexOf(';'));
	}

	private String onboardingBody() {
		return """
			{"housingType":"MONTHLY_RENT","employmentType":"EMPLOYEE","incomeRegularity":"REGULAR","hasDependents":false,"primaryConcern":"SAVING","changePace":"BALANCED","riskTolerance":"CONSERVATIVE","notificationPreference":"IMPORTANT_ONLY","contextTags":["newcomer"],"profileConsentVersion":"profile-consent-v1.0"}
			""";
	}
}
