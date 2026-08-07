package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * vNext 도메인을 걷어낸 뒤 인증이 그대로 도는지 고정한다.
 *
 * 스키마를 V1부터 다시 짰고 finmate_user에서 컬럼 20개를 뺐다. 실제 Postgres에
 * Flyway를 태워 가입→로그인이 끝까지 도는 것을 확인해야 "걷어내도 안 깨졌다"고 말할 수 있다.
 * ddl-auto=validate라 엔티티와 스키마가 어긋나면 컨텍스트 로딩에서 먼저 터진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 가입한_계정으로_로그인하면_액세스_토큰과_리프레시_쿠키를_받는다() throws Exception {
		String email = "ledger-" + System.nanoTime() + "@example.com";
		String body = """
			{"email":"%s","password":"a-long-enough-password","displayName":"지혜"}
			""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.user.email").value(email))
			.andExpect(jsonPath("$.user.displayName").value("지혜"))
			.andExpect(jsonPath("$.accessToken").isNotEmpty());

		MvcResult login = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"a-long-enough-password"}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();

		// refresh token은 본문이 아니라 쿠키로만 나간다 — 본문에 실리면 XSS로 새어 나간다
		assertThat(login.getResponse().getCookie("finmate_refresh")).isNotNull();
		assertThat(login.getResponse().getContentAsString()).doesNotContain("finmate_refresh");
	}

	@Test
	void 같은_이메일로_두_번_가입하면_409를_돌려준다() throws Exception {
		String email = "dup-" + System.nanoTime() + "@example.com";
		String body = """
			{"email":"%s","password":"a-long-enough-password","displayName":"지혜"}
			""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict());
	}
}
