package com.gagastudio.finmate.auth;

import java.util.Locale;

public final class EmailNormalizer {
	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
