package com.gagastudio.finmate.auth;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		return hasUtf8LengthBetween(password, 12, 72);
	}

	public static boolean isValidForLogin(String password) {
		return hasUtf8LengthBetween(password, 1, 72);
	}

	private static boolean hasUtf8LengthBetween(String password, int minimum, int maximum) {
		if (password == null) {
			return false;
		}
		int utf8Length = password.getBytes(StandardCharsets.UTF_8).length;
		return utf8Length >= minimum && utf8Length <= maximum;
	}
}
