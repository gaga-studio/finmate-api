package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	@Test
	void acceptsPasswordsFromTwelveThroughSeventyTwoCharacters() {
		assertThat(PasswordPolicy.isValid("a".repeat(12))).isTrue();
		assertThat(PasswordPolicy.isValid("a".repeat(72))).isTrue();
	}

	@Test
	void rejectsPasswordsOutsideAllowedLength() {
		assertThat(PasswordPolicy.isValid("a".repeat(11))).isFalse();
		assertThat(PasswordPolicy.isValid("a".repeat(73))).isFalse();
	}
}
