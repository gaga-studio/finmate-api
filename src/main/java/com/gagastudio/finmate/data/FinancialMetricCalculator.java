package com.gagastudio.finmate.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FinancialMetricCalculator {
	public static final String CALCULATION_VERSION = "financial-metrics-v1.0";
	private static final int MAX_BPS = 10_000;
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	public FinancialMetricSnapshot calculate(List<FinancialActivityInput> activities, YearMonth targetMonth,
		boolean investmentParticipant) {
		Objects.requireNonNull(activities, "activities");
		Objects.requireNonNull(targetMonth, "targetMonth");

		YearMonth firstIncomeMonth = targetMonth.minusMonths(2);
		Map<YearMonth, Long> incomeByMonth = new LinkedHashMap<>();
		long essentialSpending = 0;
		long discretionarySpending = 0;
		long savingNetInflow = 0;
		long investmentNetInflow = 0;

		for (FinancialActivityInput activity : activities) {
			YearMonth activityMonth = YearMonth.from(activity.occurredAt().atZone(SEOUL));
			if (activity.classification().equals("EARNED_INCOME")
				&& activity.direction().equals("INFLOW")
				&& !activityMonth.isBefore(firstIncomeMonth)
				&& !activityMonth.isAfter(targetMonth)) {
				incomeByMonth.merge(activityMonth, activity.amountKrw(), Long::sum);
			}

			if (!activityMonth.equals(targetMonth)) {
				continue;
			}
			switch (activity.classification()) {
				case "ESSENTIAL_EXPENSE" -> essentialSpending += outflowAmount(activity);
				case "DISCRETIONARY_EXPENSE" -> discretionarySpending += outflowAmount(activity);
				case "SAVING_CONTRIBUTION" -> savingNetInflow += contributionAmount(activity);
				case "BROKERAGE_TRANSFER" -> investmentNetInflow += contributionAmount(activity);
				default -> {
					// Unclassified activities remain available for audit but do not affect this calculation version.
				}
			}
		}

		long incomeAverage = roundedAverage(incomeByMonth.values());
		long disposableIncome = incomeAverage - essentialSpending;
		boolean sufficient = incomeByMonth.size() == 3 && disposableIncome > 0;
		Integer consumptionRate = sufficient ? toBps(discretionarySpending, disposableIncome) : null;
		Integer savingRate = sufficient ? toBps(Math.max(0, savingNetInflow), disposableIncome) : null;
		Integer investmentRate = sufficient
			? (investmentParticipant ? toBps(Math.max(0, investmentNetInflow), disposableIncome) : 0)
			: null;

		return new FinancialMetricSnapshot(
			targetMonth,
			incomeAverage,
			essentialSpending,
			disposableIncome,
			discretionarySpending,
			savingNetInflow,
			investmentNetInflow,
			consumptionRate,
			savingRate,
			investmentRate,
			CALCULATION_VERSION,
			sufficient ? "FRESH" : "INSUFFICIENT",
			activities.stream().map(FinancialActivityInput::occurredAt).max(java.time.Instant::compareTo).orElse(null));
	}

	private static long outflowAmount(FinancialActivityInput activity) {
		return activity.direction().equals("OUTFLOW") ? activity.amountKrw() : 0;
	}

	private static long contributionAmount(FinancialActivityInput activity) {
		return activity.direction().equals("OUTFLOW") ? activity.amountKrw() : -activity.amountKrw();
	}

	private static long roundedAverage(Iterable<Long> values) {
		long total = 0;
		int count = 0;
		for (Long value : values) {
			total += value;
			count++;
		}
		if (count == 0) {
			return 0;
		}
		return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact();
	}

	private static int toBps(long numerator, long denominator) {
		int bps = BigDecimal.valueOf(numerator)
			.multiply(BigDecimal.valueOf(MAX_BPS))
			.divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
			.intValue();
		return Math.max(0, Math.min(MAX_BPS, bps));
	}
}
