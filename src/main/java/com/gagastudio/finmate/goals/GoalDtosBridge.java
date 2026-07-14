package com.gagastudio.finmate.goals;

/** Stable public projection types shared with non-goal API packages. */
public final class GoalDtosBridge {
	private GoalDtosBridge() {
	}

	public record FinancialStats(int spendingDefenseBps, int savingHpBps,
		int investmentJudgmentBps, int questXp) {
	}
}
