package com.gagastudio.finmate.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import com.gagastudio.finmate.config.FinmateProperties;

@Component
class RefreshCookieFactory {
	private final FinmateProperties properties;

	RefreshCookieFactory(FinmateProperties properties) {
		this.properties = properties;
	}

	ResponseCookie refreshCookie(String token) {
		return cookie(token).maxAge(Duration.ofDays(30)).build();
	}

	ResponseCookie clearCookie() {
		return cookie("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder cookie(String value) {
		return ResponseCookie.from("finmate_refresh", value)
			.httpOnly(true)
			.secure(properties.refreshCookieSecure())
			.sameSite("Lax")
			.path("/api/v1/auth");
	}
}
