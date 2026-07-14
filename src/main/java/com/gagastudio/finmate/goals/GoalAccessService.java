package com.gagastudio.finmate.goals;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GoalAccessService {
	private final UserGoalRepository goals;
	private final SyntheticFinancialSnapshotRepository snapshots;

	GoalAccessService(UserGoalRepository goals, SyntheticFinancialSnapshotRepository snapshots) {
		this.goals = goals;
		this.snapshots = snapshots;
	}

	public boolean hasActiveGoal(UUID userId) {
		return goals.findByUserIdAndState(userId, "ACTIVE").isPresent();
	}

	public void requireActiveGoal(UUID userId) {
		if (!hasActiveGoal(userId)) throw new GoalRequiredException();
	}

	public RoutineGoalContext routineContext(UUID userId) {
		UserGoal goal = goals.findByUserIdAndState(userId, "ACTIVE").orElseThrow(GoalRequiredException::new);
		long remainingKrw = Math.max(0, goal.getTargetAmountKrw() - goal.getCurrentAmountKrw());
		YearMonth currentMonth = YearMonth.now(ZoneId.of("Asia/Seoul"));
		YearMonth targetMonth = YearMonth.from(goal.getTargetMonth());
		long remainingMonths = Math.max(1, ChronoUnit.MONTHS.between(currentMonth, targetMonth));
		long standardMonthlyAmountKrw = roundUp(remainingKrw / (double) remainingMonths, 10_000L);
		int savingRateBps = snapshots.findTopByUserIdAndGoalIdOrderByLastSyncedAtDesc(userId, goal.getId())
			.map(SyntheticFinancialSnapshot::getSavingBps).orElse(1_800);
		return new RoutineGoalContext(remainingKrw, (int) remainingMonths, standardMonthlyAmountKrw, savingRateBps);
	}

	private long roundUp(double value, long unit) {
		return Math.max(unit, (long) Math.ceil(value / unit) * unit);
	}

	public record RoutineGoalContext(long remainingKrw, int remainingMonths, long standardMonthlyAmountKrw,
		int savingRateBps) {
	}
}
