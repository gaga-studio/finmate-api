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

	public static int bossHpBpsForHighestProgress(int highestProgressBps) {
		int clampedProgress = Math.clamp(highestProgressBps, 0, MAX_BPS);
		int stage = stageForHighestProgress(clampedProgress);
		int stageStart = switch (stage) {
			case 1 -> 0;
			case 2 -> 3_300;
			case 3 -> 6_600;
			default -> throw new IllegalStateException("Unexpected raid stage");
		};
		int stageEnd = switch (stage) {
			case 1 -> 3_300;
			case 2 -> 6_600;
			case 3 -> MAX_BPS;
			default -> throw new IllegalStateException("Unexpected raid stage");
		};
		long completedWithinStage = clampedProgress - stageStart;
		long stageWidth = stageEnd - stageStart;
		return MAX_BPS - (int) (completedWithinStage * MAX_BPS / stageWidth);
	}
}
