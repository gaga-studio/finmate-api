package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gagastudio.finmate.config.FinmateProperties;
import org.junit.jupiter.api.Test;

class RefreshCookieFactoryTest {
	@Test
	void includesSecureAttributeWhenConfigured() {
		RefreshCookieFactory factory = new RefreshCookieFactory(
			new FinmateProperties("http://localhost:3000", "test-signing-secret-that-is-at-least-thirty-two-bytes", true));

		assertThat(factory.refreshCookie("opaque-token").toString()).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
	}
}
