package com.gagastudio.finmate.mate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class RoutineRequestFingerprint {
	private RoutineRequestFingerprint() {
	}

	static String importCandidate(UUID adaptationId, String candidateId) {
		return sha256("adaptationId=" + adaptationId + "&candidateId=" + candidateId);
	}

	static String replacement(UUID adaptationId, String candidateId, boolean confirmReplacement) {
		return sha256("adaptationId=" + adaptationId + "&candidateId=" + candidateId + "&confirmReplacement=" + confirmReplacement);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
