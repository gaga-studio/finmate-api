package com.gagastudio.finmate.quests;

import java.time.Instant;
import java.util.List;

final class QuestDtos {
	private QuestDtos() {
	}

	record QuestView(String questId, String title, String status, String verificationKind,
		int currentValue, int targetValue, String unit, String durationLabel, int xpReward,
		int pointReward, boolean financialStatsChanged, Instant acceptedAt,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record QuestPage(List<QuestView> items, int completedTodayCount, int totalTodayCount, int totalXp,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record QuestAcceptanceView(QuestView quest, Instant acceptedAt, boolean financialStatsChanged) {
	}

	record QuestCompletionView(QuestView quest, int xpAwarded, int pointsAwarded,
		boolean financialStatsChanged) {
	}
}
