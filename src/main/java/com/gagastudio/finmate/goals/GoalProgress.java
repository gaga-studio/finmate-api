package com.gagastudio.finmate.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GoalProgress {
	private static final int MAX_BPS = 10_000;

	private GoalProgress() {
	}

	public static int normalizedBps(long confirmedBaselineAmountKrw, long targetAmountKrw, long measuredAmountKrw) {
		if (targetAmountKrw <= confirmedBaselineAmountKrw) {
			throw new IllegalArgumentException("Target amount must exceed confirmed baseline");
		}
		BigDecimal numerator = BigDecimal.valueOf(measuredAmountKrw).subtract(BigDecimal.valueOf(confirmedBaselineAmountKrw));
		BigDecimal denominator = BigDecimal.valueOf(targetAmountKrw).subtract(BigDecimal.valueOf(confirmedBaselineAmountKrw));
		int progress = numerator.multiply(BigDecimal.valueOf(MAX_BPS)).divide(denominator, 0, RoundingMode.DOWN).intValue();
		return Math.clamp(progress, 0, MAX_BPS);
	}

	public static int stageForHighestProgress(int highestProgressBps) {
		if (highestProgressBps >= 6_600) return 3;
		if (highestProgressBps >= 3_300) return 2;
		return 1;
	}
}
