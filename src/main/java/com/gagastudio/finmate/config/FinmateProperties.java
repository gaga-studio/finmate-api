package com.gagastudio.finmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finmate")
public record FinmateProperties(String appOrigin, String jwtSecret, boolean refreshCookieSecure) {
}
