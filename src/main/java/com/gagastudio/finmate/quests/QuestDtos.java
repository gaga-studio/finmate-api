package com.gagastudio.finmate.quests;

import java.time.Instant;
import java.util.List;

final class QuestDtos {
	private QuestDtos() {
	}
	record QuestView(String questId, String title, String status, String verificationKind, int xpReward,
		int pointReward, boolean financialStatsChanged, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}
	record QuestPage(List<QuestView> items, int totalXp, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
	record QuestCompletionView(QuestView quest, int xpAwarded, int pointsAwarded, boolean financialStatsChanged) {
	}
}
