package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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

	@Test
	void measuresPasswordLengthInUtf8Bytes() {
		String twelveBytes = "가".repeat(4);
		String ninetyBytes = "가".repeat(30);

		assertThat(twelveBytes.getBytes(StandardCharsets.UTF_8)).hasSize(12);
		assertThat(PasswordPolicy.isValid(twelveBytes)).isTrue();
		assertThat(ninetyBytes.getBytes(StandardCharsets.UTF_8)).hasSize(90);
		assertThat(PasswordPolicy.isValid(ninetyBytes)).isFalse();
	}
}
