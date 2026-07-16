package com.gagastudio.finmate.runtime;

import com.gagastudio.finmate.data.FinancialMetricCalculator;
import java.time.Instant;
import java.time.YearMonth;

public record RuntimeFinancialSummary(
	YearMonth month,
	Long disposableIncomeKrw,
	Integer consumptionRateBps,
	Integer savingRateBps,
	Integer investmentRateBps,
	String calculationVersion,
	String dataState,
	Instant lastSyncedAt
) {
	public static RuntimeFinancialSummary insufficient(YearMonth month, Instant lastSyncedAt) {
		return new RuntimeFinancialSummary(month, null, null, null, null,
			FinancialMetricCalculator.CALCULATION_VERSION, "INSUFFICIENT", lastSyncedAt);
	}

	public boolean isFresh() {
		return "FRESH".equals(dataState);
	}
}
