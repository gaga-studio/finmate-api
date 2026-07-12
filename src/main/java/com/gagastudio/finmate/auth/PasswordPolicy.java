package com.gagastudio.finmate.auth;

public final class PasswordPolicy {
	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		return password != null && password.length() >= 12 && password.length() <= 72;
	}
}
