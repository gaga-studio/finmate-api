package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GoalService {
	private static final String ACTIVE = "ACTIVE";
	private final OnboardingStateRepository onboardingStates;
	private final UserGoalRepository goals;
	private final SyntheticFinancialSnapshotRepository snapshots;
	private final RaidProjectionRepository raids;
	private final RaidProjectionAuditRepository raidAudits;
	private final GoalValidator validator;
	private final RaidProgressProjector projector;

	GoalService(OnboardingStateRepository onboardingStates, UserGoalRepository goals,
		SyntheticFinancialSnapshotRepository snapshots, RaidProjectionRepository raids,
		RaidProjectionAuditRepository raidAudits, GoalValidator validator, RaidProgressProjector projector) {
		this.onboardingStates = onboardingStates;
		this.goals = goals;
		this.snapshots = snapshots;
		this.raids = raids;
		this.raidAudits = raidAudits;
		this.validator = validator;
		this.projector = projector;
	}

	GoalDtos.OnboardingView onboarding(UUID userId) {
		OnboardingState state = onboardingStates.findById(userId).orElse(null);
		if (state == null) return new GoalDtos.OnboardingView("DRAFT", null, null);
		return new GoalDtos.OnboardingView(state.getStatus(), state.getDisplayName(), activeGoal(userId));
	}

	@Transactional
	GoalDtos.OnboardingView completeOnboarding(UUID userId, String idempotencyKey, GoalDtos.CompleteOnboardingRequest request) {
		validateIdempotencyKey(idempotencyKey);
		GoalDraft draft = request.mainGoal().toDraft();
		validator.validate(draft, request.confirmMainGoal());
		if (goals.existsByUserIdAndState(userId, ACTIVE)) {
			OnboardingState state = onboardingStates.findById(userId).orElseThrow(ActiveMainGoalException::new);
			if (state.hasIdempotencyKey(idempotencyKey)) {
				return new GoalDtos.OnboardingView(state.getStatus(), state.getDisplayName(), activeGoal(userId));
			}
			throw new ActiveMainGoalException();
		}

		Instant now = Instant.now();
		UserGoal goal = goals.save(new UserGoal(userId, draft, now, now));
		SyntheticFinancialSnapshot snapshot = snapshots.save(new SyntheticFinancialSnapshot(userId, goal, now));
		int progress = projector.progressBps(goal.getCurrentAmountKrw(), goal.getTargetAmountKrw(), snapshot.toData());
		RaidProjection raid = raids.save(new RaidProjection(goal, progress, now));
		raidAudits.save(new RaidProjectionAudit(raid, now));
		OnboardingState state = onboardingStates.save(new OnboardingState(userId, request.displayName().trim(), now, idempotencyKey));
		return new GoalDtos.OnboardingView(state.getStatus(), state.getDisplayName(), goalView(goal));
	}

	GoalDtos.UserGoalView activeGoal(UUID userId) {
		return goalView(activeGoalEntity(userId));
	}

	GoalDtos.HomeView home(UUID userId) {
		UserGoal goal = activeGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId);
		RaidProjection raid = raid(goal);
		return new GoalDtos.HomeView(goalView(goal), raidView(raid, snapshot, "HOME_GOAL_CONFIRMED_V1"), null, null,
			"home-calc-v1", snapshot == null ? "INSUFFICIENT" : "FRESH", snapshot == null ? null : snapshot.getLastSyncedAt());
	}

	GoalDtos.RaidView currentRaid(UUID userId) {
		UserGoal goal = activeGoalEntity(userId);
		return raidView(raid(goal), latestSnapshot(userId), null);
	}

	GoalDtos.MonthlyReportView monthlyReport(UUID userId, String month) {
		YearMonth requestedMonth;
		try {
			requestedMonth = YearMonth.parse(month);
		} catch (RuntimeException exception) {
			throw new InvalidReportMonthException();
		}
		UserGoal goal = activeGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = snapshots.findTopByUserIdAndSnapshotMonthOrderByLastSyncedAtDesc(userId, requestedMonth.atDay(1)).orElse(null);
		if (snapshot == null) {
			return new GoalDtos.MonthlyReportView(month, 0, new GoalDtos.FinancialStatsView(0, 0, 0), 0, 0,
				"report-calc-v1", "INSUFFICIENT", null);
		}
		int progress = projector.progressBps(goal.getCurrentAmountKrw(), goal.getTargetAmountKrw(), snapshot.toData());
		return new GoalDtos.MonthlyReportView(month, progress, financialStats(snapshot), snapshot.getXp(), 0,
			"report-calc-v1", "FRESH", snapshot.getLastSyncedAt());
	}

	private UserGoal activeGoalEntity(UUID userId) {
		return goals.findByUserIdAndState(userId, ACTIVE).orElseThrow(MainGoalNotFoundException::new);
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidMainGoalException("Idempotency-Key must contain 16 to 128 characters");
		}
	}

	private SyntheticFinancialSnapshot latestSnapshot(UUID userId) {
		return snapshots.findTopByUserIdOrderByLastSyncedAtDesc(userId).orElse(null);
	}

	private RaidProjection raid(UserGoal goal) {
		return raids.findByGoalId(goal.getId()).orElseThrow(MainGoalNotFoundException::new);
	}

	private GoalDtos.UserGoalView goalView(UserGoal goal) {
		return new GoalDtos.UserGoalView(goal.getId().toString(), goal.getTitle(), goal.getDomain(), goal.getCurrentAmountKrw(),
			goal.getTargetAmountKrw(), YearMonth.from(goal.getTargetMonth()).toString(), goal.getState(), goal.getConfirmedAt(),
			goal.getCalculationVersion(), goal.getDataState(), goal.getLastSyncedAt());
	}

	private GoalDtos.RaidView raidView(RaidProjection raid, SyntheticFinancialSnapshot snapshot, String coachCopyKey) {
		GoalDtos.FinancialStatsView stats = snapshot == null ? new GoalDtos.FinancialStatsView(0, 0, 0) : financialStats(snapshot);
		int xp = snapshot == null ? 0 : snapshot.getXp();
		return new GoalDtos.RaidView(raid.getId().toString(), raid.getGoalId().toString(), raid.getStage(), raid.getBossHpBps(),
			raid.getCurrentProgressBps(), stats, xp, coachCopyKey == null ? raid.getCoachCopyKey() : coachCopyKey,
			raid.getCalculationVersion(), raid.getDataState(), raid.getLastSyncedAt());
	}

	private GoalDtos.FinancialStatsView financialStats(SyntheticFinancialSnapshot snapshot) {
		return new GoalDtos.FinancialStatsView(snapshot.getSpendingBps(), snapshot.getSavingBps(), snapshot.getInvestmentJudgmentBps());
	}
}
