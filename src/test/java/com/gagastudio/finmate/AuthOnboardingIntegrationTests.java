package com.gagastudio.finmate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
	"finmate.jwt-secret=test-signing-secret-that-is-at-least-thirty-two-bytes",
	"spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthOnboardingIntegrationTests {
	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtEncoder jwtEncoder;

	@Test
	void healthEndpointIsAvailableWithoutAnAccessToken() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void signupCreatesAnAuthenticatedSessionForANormalizedEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"MinJi.Kim@Example.COM","password":"FinMate!2026#","displayName":"Minji"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(cookie().exists("finmate_refresh"))
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
				org.hamcrest.Matchers.containsString("HttpOnly"),
				org.hamcrest.Matchers.containsString("SameSite=Lax"),
				org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
				org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secure")))))
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.user.email").value("minji.kim@example.com"));
	}

	@Test
	void duplicateSignupReturnsAConflictProblem() throws Exception {
		signUp("duplicate@example.com");

		assertCompleteProblem(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signUpBody("duplicate@example.com")), 409, "DUPLICATE_EMAIL");
	}

	@Test
	void loginWithWrongPasswordReturnsAnUnauthorizedProblem() throws Exception {
		signUp("login-failure@example.com");

		assertCompleteProblem(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login-failure@example.com\",\"password\":\"wrong-password\"}"), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void loginRejectsPasswordLongerThanSeventyTwoUtf8BytesBeforeBcryptComparison() throws Exception {
		String email = "login-password-bytes@example.com";
		String password = "가".repeat(24);
		String signupBody = "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Minji\"}"
			.formatted(email, password);

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginBody(email, password)))
			.andExpect(status().isOk());
		assertCompleteProblem(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(loginBody(email, password + "가")), 400, "VALIDATION_FAILED");
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
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void forgedAccessTokenReturnsCompleteUnauthorizedProblem() throws Exception {
		String token = accessToken(signUp("forged@example.com"));
		String forged = token + "x";

		assertCompleteProblem(get("/api/v1/me").header("Authorization", "Bearer " + forged), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void expiredAccessTokenReturnsCompleteUnauthorizedProblem() throws Exception {
		String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
			JwsHeader.with(MacAlgorithm.HS256).build(),
			JwtClaimsSet.builder().subject("00000000-0000-0000-0000-000000000001")
				.issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60)).build())).getTokenValue();

		assertCompleteProblem(get("/api/v1/me").header("Authorization", "Bearer " + expiredToken), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void malformedAccessTokenReturnsCompleteUnauthorizedProblem() throws Exception {
		assertCompleteProblem(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"), 401, "INVALID_CREDENTIALS");
	}

	@Test
	void malformedJsonReturnsCompleteValidationProblem() throws Exception {
		assertCompleteProblem(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"), 400, "VALIDATION_FAILED");
	}

	@Test
	void signupRejectsPasswordLongerThanSeventyTwoUtf8BytesAsValidationProblem() throws Exception {
		String password = "가".repeat(30);
		String body = "{\"email\":\"password-bytes@example.com\",\"password\":\"%s\",\"displayName\":\"Minji\"}"
			.formatted(password);

		assertCompleteProblem(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body),
			400, "VALIDATION_FAILED");
	}

	@Test
	void rejectsCorsRequestsFromUnconfiguredOrigins() throws Exception {
		mockMvc.perform(options("/api/v1/auth/signup")
				.header("Origin", "https://untrusted.example")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void allowsCorsPreflightFromConfiguredOriginWithCredentials() throws Exception {
		mockMvc.perform(options("/api/v1/auth/signup")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
			.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
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
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors").isArray());
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
	void goalOnboardingCompletionPersistsAcrossLoginSessions() throws Exception {
		MvcResult signup = signUp("goal-onboarding-login@example.com");
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "goal-onboarding-login-key-001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"Minji","mainGoal":{"title":"Europe travel","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginBody("goal-onboarding-login@example.com", "FinMate!2026#")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.onboardingStatus").value("COMPLETED"));
	}

	@Test
	void goalOnboardingRejectsDisplayNamesLongerThanTheUserColumnLimit() throws Exception {
		MvcResult signup = signUp("goal-onboarding-name-limit@example.com");
		mockMvc.perform(put("/api/v1/onboarding")
				.header("Authorization", "Bearer " + accessToken(signup))
				.header("Idempotency-Key", "goal-onboarding-name-limit-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"1234567890123456789012345678901","mainGoal":{"title":"Europe travel","domain":"SAVING","currentAmountKrw":2000000,"targetAmountKrw":5000000,"targetMonth":"2027-01"},"confirmMainGoal":true}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
	void concurrentRefreshAllowsOnlyOneSuccessor() throws Exception {
		String refresh = refreshToken(signUp("concurrent-refresh@example.com"));
		CyclicBarrier start = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> refreshRequest = () -> {
				start.await();
				return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("finmate_refresh", refresh)))
					.andReturn().getResponse().getStatus();
			};
			List<Future<Integer>> responses = executor.invokeAll(List.of(refreshRequest, refreshRequest));
			List<Integer> statuses = List.of(responses.get(0).get(), responses.get(1).get()).stream().sorted().toList();
			Assertions.assertThat(statuses).containsExactly(200, 401);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentSignupMapsTheUniqueEmailRaceToAConflictProblem() throws Exception {
		CyclicBarrier start = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<MvcResult> request = () -> {
				start.await();
				return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
					.content(signUpBody("signup-race@example.com"))).andReturn();
			};
			List<Future<MvcResult>> responses = executor.invokeAll(List.of(request, request));
			List<MvcResult> results = List.of(responses.get(0).get(), responses.get(1).get());
			List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).sorted().toList();
			Assertions.assertThat(statuses).containsExactly(201, 409);
			results.stream().filter(result -> result.getResponse().getStatus() == 409).forEach(result -> {
				try {
					Assertions.assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("code").asText())
						.isEqualTo("DUPLICATE_EMAIL");
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			});
		} finally {
			executor.shutdownNow();
		}
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

	private String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
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

	private void assertCompleteProblem(org.springframework.test.web.servlet.RequestBuilder request, int status, String code) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.type").exists())
			.andExpect(jsonPath("$.title").exists())
			.andExpect(jsonPath("$.status").value(status))
			.andExpect(jsonPath("$.detail").exists())
			.andExpect(jsonPath("$.instance").exists())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.traceId").exists());
	}
}
