package com.gagastudio.finmate.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FinmatePropertiesTest {
	@Test
	void rejectsJwtSecretsShorterThanThirtyTwoBytes() {
		assertThatThrownBy(() -> new FinmateProperties("http://localhost:3000", "too-short", false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("FINMATE_JWT_SECRET");
	}
}
