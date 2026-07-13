package com.gagastudio.finmate.data;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record FinancialActivityInput(
	String activityType,
	String classification,
	String direction,
	long amountKrw,
	Instant occurredAt
) {
	public FinancialActivityInput {
		activityType = normalize(activityType, "activityType");
		classification = normalize(classification, "classification");
		direction = normalize(direction, "direction");
		if (!direction.equals("INFLOW") && !direction.equals("OUTFLOW")) {
			throw new IllegalArgumentException("direction must be INFLOW or OUTFLOW");
		}
		if (amountKrw < 0) {
			throw new IllegalArgumentException("amountKrw must not be negative");
		}
		occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
	}

	private static String normalize(String value, String field) {
		String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return normalized;
	}
}
