package com.gagastudio.finmate.auth;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		if (password == null) {
			return false;
		}
		int utf8Length = password.getBytes(StandardCharsets.UTF_8).length;
		return utf8Length >= 12 && utf8Length <= 72;
	}
}
