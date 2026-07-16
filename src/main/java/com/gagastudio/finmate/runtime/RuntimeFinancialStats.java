package com.gagastudio.finmate.runtime;

import java.time.Instant;

public record RuntimeFinancialStats(
	Integer spendingDefenseBps,
	Integer savingHpBps,
	Integer investmentJudgmentBps,
	int questXp,
	String calculationVersion,
	String dataState,
	Instant lastSyncedAt
) {
}
