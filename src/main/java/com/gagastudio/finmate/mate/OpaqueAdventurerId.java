package com.gagastudio.finmate.mate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OpaqueAdventurerId {
	private OpaqueAdventurerId() {
	}

	static String from(String releaseVersion, String sourcePersonaId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((releaseVersion + ":" + sourcePersonaId).getBytes(StandardCharsets.UTF_8));
			return "adv-" + HexFormat.of().formatHex(digest).substring(0, 16);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
