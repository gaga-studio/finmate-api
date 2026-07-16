package com.gagastudio.finmate.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialMetricCalculatorTests {
	private final FinancialMetricCalculator calculator = new FinancialMetricCalculator();

	@Test
	void recalculatesMonthlyMetricsFromSanitizedActivitiesInsteadOfL3Scores() {
		List<FinancialActivityInput> activities = List.of(
			activity("2026-01-09T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2026-02-09T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2026-03-09T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2026-03-10T09:00:00Z", "SPENDING", "ESSENTIAL_EXPENSE", "OUTFLOW", 800_000),
			activity("2026-03-11T09:00:00Z", "SPENDING", "DISCRETIONARY_EXPENSE", "OUTFLOW", 400_000),
			activity("2026-03-12T09:00:00Z", "SAVING", "SAVING_CONTRIBUTION", "OUTFLOW", 300_000),
			activity("2026-03-13T09:00:00Z", "INVESTMENT", "BROKERAGE_TRANSFER", "OUTFLOW", 100_000));

		FinancialMetricSnapshot result = calculator.calculate(activities, YearMonth.of(2026, 3), true);

		assertThat(result.incomeTrailingAverageKrw()).isEqualTo(2_000_000);
		assertThat(result.essentialSpendingKrw()).isEqualTo(800_000);
		assertThat(result.disposableIncomeKrw()).isEqualTo(1_200_000);
		assertThat(result.discretionarySpendingKrw()).isEqualTo(400_000);
		assertThat(result.savingNetInflowKrw()).isEqualTo(300_000);
		assertThat(result.investmentNetInflowKrw()).isEqualTo(100_000);
		assertThat(result.consumptionRateBps()).isEqualTo(3333);
		assertThat(result.savingRateBps()).isEqualTo(2500);
		assertThat(result.investmentContributionRateBps()).isEqualTo(833);
		assertThat(result.dataState()).isEqualTo("FRESH");
		assertThat(result.calculationVersion()).isEqualTo("financial-metrics-v1.0");
	}

	@Test
	void returnsInsufficientAndNoRatesWhenDisposableIncomeIsNotPositive() {
		List<FinancialActivityInput> activities = List.of(
			activity("2026-03-09T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 500_000),
			activity("2026-03-10T09:00:00Z", "SPENDING", "ESSENTIAL_EXPENSE", "OUTFLOW", 600_000),
			activity("2026-03-11T09:00:00Z", "SPENDING", "DISCRETIONARY_EXPENSE", "OUTFLOW", 100_000));

		FinancialMetricSnapshot result = calculator.calculate(activities, YearMonth.of(2026, 3), false);

		assertThat(result.disposableIncomeKrw()).isEqualTo(-100_000);
		assertThat(result.consumptionRateBps()).isNull();
		assertThat(result.savingRateBps()).isNull();
		assertThat(result.investmentContributionRateBps()).isNull();
		assertThat(result.dataState()).isEqualTo("INSUFFICIENT");
	}

	@Test
	void matchesTheClampedL3GoldenMetricForSyntheticPersonaP0001() {
		List<FinancialActivityInput> activities = List.of(
			activity("2026-01-31T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 1_201_661),
			activity("2026-02-28T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 1_201_516),
			activity("2026-03-31T09:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 1_112_663),
			activity("2026-03-31T09:00:01Z", "SPENDING", "ESSENTIAL_EXPENSE", "OUTFLOW", 358_953),
			activity("2026-03-31T09:00:02Z", "SPENDING", "DISCRETIONARY_EXPENSE", "OUTFLOW", 1_007_211),
			activity("2026-03-31T09:00:03Z", "SAVING", "SAVING_CONTRIBUTION", "OUTFLOW", 90_000));

		FinancialMetricSnapshot result = calculator.calculate(activities, YearMonth.of(2026, 3), false);

		assertThat(result.incomeTrailingAverageKrw()).isEqualTo(1_171_947);
		assertThat(result.disposableIncomeKrw()).isEqualTo(812_994);
		assertThat(result.consumptionRateBps()).isEqualTo(10_000);
		assertThat(result.savingRateBps()).isEqualTo(1_107);
		assertThat(result.investmentContributionRateBps()).isZero();
		assertThat(result.dataState()).isEqualTo("FRESH");
	}

	@Test
	void assignsMonthBoundaryActivitiesUsingAsiaSeoul() {
		List<FinancialActivityInput> activities = List.of(
			activity("2025-09-01T00:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2025-10-01T00:00:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2025-10-31T15:30:00Z", "INCOME", "EARNED_INCOME", "INFLOW", 2_000_000),
			activity("2025-10-31T16:00:00Z", "SPENDING", "ESSENTIAL_EXPENSE", "OUTFLOW", 800_000));

		FinancialMetricSnapshot result = calculator.calculate(activities, YearMonth.of(2025, 11), false);

		assertThat(result.dataState()).isEqualTo("FRESH");
		assertThat(result.incomeTrailingAverageKrw()).isEqualTo(2_000_000);
		assertThat(result.essentialSpendingKrw()).isEqualTo(800_000);
	}

	private FinancialActivityInput activity(String occurredAt, String activityType, String classification,
		String direction, long amountKrw) {
		return new FinancialActivityInput(activityType, classification, direction, amountKrw, Instant.parse(occurredAt));
	}
}
