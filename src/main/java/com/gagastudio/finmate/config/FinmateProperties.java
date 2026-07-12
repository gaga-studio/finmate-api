package com.gagastudio.finmate.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finmate")
public record FinmateProperties(String appOrigin, String jwtSecret, boolean refreshCookieSecure) {
	public FinmateProperties {
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("FINMATE_JWT_SECRET must be at least 32 bytes");
		}
	}
}
