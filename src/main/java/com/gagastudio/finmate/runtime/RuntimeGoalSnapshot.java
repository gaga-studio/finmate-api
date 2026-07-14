package com.gagastudio.finmate.runtime;

import java.time.Instant;
import java.time.YearMonth;

public record RuntimeGoalSnapshot(
	YearMonth month,
	int spendingRateBps,
	int savingRateBps,
	int investmentRateBps,
	Instant lastSyncedAt
) {
}
