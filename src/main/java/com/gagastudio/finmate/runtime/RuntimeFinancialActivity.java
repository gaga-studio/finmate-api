package com.gagastudio.finmate.runtime;

import java.time.Instant;

public record RuntimeFinancialActivity(
	String sourceTransactionId,
	String activityType,
	String direction,
	String classification,
	String category,
	String subcategory,
	String displayLabel,
	long amountKrw,
	Instant occurredAt
) {
}
