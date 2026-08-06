package com.gagastudio.finmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.metrics.MonthlyRollup;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 가입한 사람이 자기 화면을 토큰으로 보는지 확인한다.
 *
 * 그전까지는 persona를 경로로 받아 인증 없이 열려 있었다. 그건 남의 id만 알면 남의 원장을
 * 볼 수 있다는 뜻이고, 금융 데이터에서는 그것만으로 끝이다.
 */
@SpringBootTest(properties = "finmate.art.provider=stub")
@AutoConfigureMockMvc
class MeFlowIntegrationTest extends PostgresIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private LedgerImporter importer;
	@Autowired private MonthlyRollup rollup;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper json;

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@BeforeEach
	void 적재한다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다. pipeline/05_generate.py를 먼저 실행하세요")
			.isTrue();
		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month, diary_entry CASCADE");
		jdbc.execute("DELETE FROM finmate_refresh");
		jdbc.execute("DELETE FROM finmate_user");
		importer.importFrom(bundlesDir(), 20);
		rollup.rebuildAll();
	}

	private String signUp() throws Exception {
		String body = """
			{"email":"me-%d@example.com","password":"a-long-enough-password","displayName":"지혜"}
			""".formatted(System.nanoTime());
		String res = mockMvc.perform(post("/api/v1/auth/signup")
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(res).get("accessToken").asText();
	}

	@Test
	void 가입하면_금융_데이터가_붙고_내_화면이_보인다() throws Exception {
		String token = signUp();

		mockMvc.perform(get("/api/v1/me/overview").param("period", "monthly")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.limit").isNumber())
			.andExpect(jsonPath("$.referenceDate").isNotEmpty())
			.andExpect(jsonPath("$.topSpends").isArray());
	}

	@Test
	void 토큰_없이는_내_화면을_볼_수_없다() throws Exception {
		mockMvc.perform(get("/api/v1/me/overview")).andExpect(status().isUnauthorized());
	}

	@Test
	void 남의_persona_경로도_토큰_없이는_막힌다() throws Exception {
		UUID persona = jdbc.queryForObject("SELECT id FROM persona LIMIT 1", UUID.class);
		mockMvc.perform(get("/api/v1/personas/%s/diary/2026-07-01".formatted(persona)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 두_사람이_같은_금융_데이터를_나눠_갖지_않는다() throws Exception {
		signUp();
		signUp();

		Long distinct = jdbc.queryForObject(
			"SELECT count(DISTINCT persona_id) FROM finmate_user WHERE persona_id IS NOT NULL", Long.class);
		Long assigned = jdbc.queryForObject(
			"SELECT count(*) FROM finmate_user WHERE persona_id IS NOT NULL", Long.class);
		assertThat(distinct).isEqualTo(assigned).isEqualTo(2L);
	}

	@Test
	void 또래_비교가_내_소득대_기준으로_나온다() throws Exception {
		String token = signUp();

		String res = mockMvc.perform(get("/api/v1/me/peers")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myBand").isNotEmpty())
			.andExpect(jsonPath("$.percentile").isNumber())
			.andReturn().getResponse().getContentAsString();

		var node = json.readTree(res);
		assertThat(node.get("percentile").asInt()).isBetween(0, 100);
		assertThat(node.get("peerCount").asInt()).isPositive();
		// 그룹 목록에 내 소득대가 들어 있어야 화면이 나를 어디에 세울지 안다
		assertThat(node.get("bands").toString()).contains(node.get("myBand").asText());
	}

	@Test
	void 그림일기를_내_계정으로_요청하고_받는다() throws Exception {
		String token = signUp();

		String overview = mockMvc.perform(get("/api/v1/me/overview")
			.header("Authorization", "Bearer " + token))
			.andReturn().getResponse().getContentAsString();
		String day = json.readTree(overview).get("referenceDate").asText();

		mockMvc.perform(post("/api/v1/me/diary/" + day).header("Authorization", "Bearer " + token))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"));

		// 같은 날 다시 요청하면 새로 만들지 않는다
		mockMvc.perform(post("/api/v1/me/diary/" + day).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}
}
