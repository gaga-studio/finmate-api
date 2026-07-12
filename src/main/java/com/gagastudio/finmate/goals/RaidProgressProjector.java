package com.gagastudio.finmate.goals;

import org.springframework.stereotype.Component;

@Component
public class RaidProgressProjector {
	public int progressBps(long confirmedBaselineAmountKrw, long targetAmountKrw, FinancialSnapshotData snapshot) {
		return GoalProgress.normalizedBps(confirmedBaselineAmountKrw, targetAmountKrw, snapshot.observedGoalAmountKrw());
	}
}
