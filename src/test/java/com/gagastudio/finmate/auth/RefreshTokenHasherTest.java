package com.gagastudio.finmate.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

	@Test
	void hashesTokenDeterministicallyWithoutRetainingItsRawValue() {
		String token = "opaque-refresh-token";

		assertThat(RefreshTokenHasher.sha256(token))
			.isEqualTo("862f58013a2bd2d34eba271c56252c0e69b4715133aea31b0d0ebbb1470c3d6e")
			.isNotEqualTo(token);
	}

	@Test
	void issuesDistinctTokensForRotation() {
		String current = RefreshTokenHasher.newToken();
		String rotated = RefreshTokenHasher.newToken();

		assertThat(rotated).isNotEqualTo(current);
		assertThat(RefreshTokenHasher.sha256(rotated)).hasSize(64);
	}
}
