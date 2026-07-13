package com.gagastudio.finmate.rewards;

class RewardException extends RuntimeException {
	private final String code;

	RewardException(String code, String message) {
		super(message);
		this.code = code;
	}

	String getCode() {
		return code;
	}
}
