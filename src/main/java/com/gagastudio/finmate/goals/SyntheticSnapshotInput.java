package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.Objects;

public record SyntheticSnapshotInput(long observedGoalAmountKrw, int spendingBps, int savingBps,
	int investmentJudgmentBps, int xp, Instant lastSyncedAt) {
	public SyntheticSnapshotInput {
		if (observedGoalAmountKrw < 0) throw new IllegalArgumentException("Observed goal amount cannot be negative");
		validateBps("spendingBps", spendingBps);
		validateBps("savingBps", savingBps);
		validateBps("investmentJudgmentBps", investmentJudgmentBps);
		if (xp < 0) throw new IllegalArgumentException("XP cannot be negative");
		Objects.requireNonNull(lastSyncedAt, "lastSyncedAt");
	}

	private static void validateBps(String field, int value) {
		if (value < 0 || value > 10_000) throw new IllegalArgumentException(field + " must be between 0 and 10000");
	}
}
