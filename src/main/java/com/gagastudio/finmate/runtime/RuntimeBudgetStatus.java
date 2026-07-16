package com.gagastudio.finmate.runtime;

public record RuntimeBudgetStatus(long budgetKrw, long spentKrw, long remainingKrw, int usedBps) {
	public static RuntimeBudgetStatus empty() {
		return new RuntimeBudgetStatus(0, 0, 0, 0);
	}
}
