package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

	@Test
	void normalizesEmailToLowercaseAndTrimsOuterWhitespace() {
		assertThat(EmailNormalizer.normalize("  MinJi.Kim@Example.COM "))
			.isEqualTo("minji.kim@example.com");
	}
}
