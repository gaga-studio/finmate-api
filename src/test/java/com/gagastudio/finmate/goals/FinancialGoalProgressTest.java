package com.gagastudio.finmate.goals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FinancialGoalProgressTest {
	@Test
	void questXpDoesNotChangeFinancialGoalProgress() {
		RaidProgressProjector projector = new RaidProgressProjector();

		int withoutXp = projector.progressBps(2_000_000, 5_000_000, new FinancialSnapshotData(3_500_000, 0));
		int withQuestXp = projector.progressBps(2_000_000, 5_000_000, new FinancialSnapshotData(3_500_000, 9_999));

		assertThat(withoutXp).isEqualTo(5_000);
		assertThat(withQuestXp).isEqualTo(withoutXp);
	}
}
