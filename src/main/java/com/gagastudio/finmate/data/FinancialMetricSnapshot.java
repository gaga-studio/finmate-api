package com.gagastudio.finmate.data;

import java.time.Instant;
import java.time.YearMonth;

public record FinancialMetricSnapshot(
	YearMonth month,
	long incomeTrailingAverageKrw,
	long essentialSpendingKrw,
	long disposableIncomeKrw,
	long discretionarySpendingKrw,
	long savingNetInflowKrw,
	long investmentNetInflowKrw,
	Integer consumptionRateBps,
	Integer savingRateBps,
	Integer investmentContributionRateBps,
	String calculationVersion,
	String dataState,
	Instant lastSyncedAt
) {
}
