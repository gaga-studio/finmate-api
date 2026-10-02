package com.gagastudio.finmate.metrics;

public class InvalidPeriodException extends IllegalArgumentException {
	public InvalidPeriodException(String value) {
		super("기간은 daily · weekly · monthly 중 하나여야 합니다: " + value);
	}
}
