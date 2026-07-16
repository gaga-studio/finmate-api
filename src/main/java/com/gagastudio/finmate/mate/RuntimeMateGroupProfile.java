package com.gagastudio.finmate.mate;

import java.time.LocalDate;

record RuntimeMateGroupProfile(String groupId, String description, int memberCount, Double averageAge,
	int averageSpendingDefenseBps, int averageSavingHpBps, int averageInvestmentJudgmentBps,
	int averageConsumptionRateBps, int averageSavingRateBps, LocalDate dataAsOf) {
}
