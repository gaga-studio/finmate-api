package com.gagastudio.finmate.goals;

import com.gagastudio.finmate.auth.UserOnboardingStatusService;
import com.gagastudio.finmate.mate.MateService;
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
	private final GoalValidator validator;
	private final OnboardingCommandLock commandLock;
	private final SyntheticSnapshotIngestionService snapshotIngestion;
	private final MateService mateService;
	private final UserOnboardingStatusService onboardingStatusService;

	GoalService(OnboardingStateRepository onboardingStates, UserGoalRepository goals,
		SyntheticFinancialSnapshotRepository snapshots, RaidProjectionRepository raids, GoalValidator validator,
		OnboardingCommandLock commandLock, SyntheticSnapshotIngestionService snapshotIngestion, MateService mateService,
		UserOnboardingStatusService onboardingStatusService) {
		this.onboardingStates = onboardingStates;
		this.goals = goals;
		this.snapshots = snapshots;
		this.raids = raids;
		this.validator = validator;
		this.commandLock = commandLock;
		this.snapshotIngestion = snapshotIngestion;
		this.mateService = mateService;
		this.onboardingStatusService = onboardingStatusService;
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
		commandLock.lockUser(userId);

		UserGoal existingGoal = goals.findByUserIdAndState(userId, ACTIVE).orElse(null);
		if (existingGoal != null) {
			OnboardingState state = onboardingStates.findById(userId).orElseThrow(ActiveMainGoalException::new);
			if (state.hasIdempotencyKey(idempotencyKey)) {
				return new GoalDtos.OnboardingView(state.getStatus(), state.getDisplayName(), goalView(existingGoal));
			}
			throw new ActiveMainGoalException();
		}

		Instant now = Instant.now();
		UserGoal goal = goals.saveAndFlush(new UserGoal(userId, draft, now, now));
		OnboardingState state = onboardingStates.save(new OnboardingState(userId, request.displayName().trim(), now, idempotencyKey));
		onboardingStatusService.complete(userId, request.displayName().trim());
		snapshotIngestion.ingest(userId, new SyntheticSnapshotInput(goal.getCurrentAmountKrw(), 5_200, 1_800, 4_000, 0, now));
		return new GoalDtos.OnboardingView(state.getStatus(), state.getDisplayName(), goalView(goal));
	}

	GoalDtos.UserGoalView activeGoal(UUID userId) {
		return goalView(activeGoalEntity(userId));
	}

	GoalDtos.HomeView home(UUID userId) {
		UserGoal goal = activeGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		RaidProjection raid = raid(userId, goal);
		return new GoalDtos.HomeView(goalView(goal), raidView(raid, snapshot, "HOME_GOAL_CONFIRMED_V1"),
			mateService.activeBuildForHome(userId), null,
			"home-calc-v1", snapshot == null ? "INSUFFICIENT" : "FRESH", snapshot == null ? null : snapshot.getLastSyncedAt());
	}

	GoalDtos.RaidView currentRaid(UUID userId) {
		UserGoal goal = activeGoalEntity(userId);
		return raidView(raid(userId, goal), latestSnapshot(userId, goal.getId()), null);
	}

	GoalDtos.MonthlyReportView monthlyReport(UUID userId, String month) {
		YearMonth requestedMonth;
		try {
			requestedMonth = YearMonth.parse(month);
		} catch (RuntimeException exception) {
			throw new InvalidReportMonthException();
		}
		UserGoal goal = activeGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = snapshots
			.findTopByUserIdAndGoalIdAndSnapshotMonthOrderByLastSyncedAtDesc(userId, goal.getId(), requestedMonth.atDay(1))
			.orElse(null);
		if (snapshot == null) {
			return new GoalDtos.MonthlyReportView(month, 0, new GoalDtos.FinancialStatsView(0, 0, 0), 0, 0,
				"report-calc-v1", "INSUFFICIENT", null);
		}
		RaidProjection raid = raid(userId, goal);
		int progress = GoalProgress.normalizedBps(raid.getConfirmedBaselineAmountKrw(), goal.getTargetAmountKrw(),
			snapshot.toData().observedGoalAmountKrw());
		return new GoalDtos.MonthlyReportView(month, progress, financialStats(snapshot), snapshot.getXp(), 0,
			"report-calc-v1", "FRESH", snapshot.getLastSyncedAt());
	}

	private UserGoal activeGoalEntity(UUID userId) {
		return goals.findByUserIdAndState(userId, ACTIVE).orElseThrow(MainGoalNotFoundException::new);
	}

	private SyntheticFinancialSnapshot latestSnapshot(UUID userId, UUID goalId) {
		return snapshots.findTopByUserIdAndGoalIdOrderByLastSyncedAtDesc(userId, goalId).orElse(null);
	}

	private RaidProjection raid(UUID userId, UserGoal goal) {
		return raids.findByUserIdAndGoalId(userId, goal.getId()).orElseThrow(MainGoalNotFoundException::new);
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidMainGoalException("Idempotency-Key must contain 16 to 128 characters");
		}
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
			raid.getHighestProgressBps(), stats, xp, coachCopyKey == null ? raid.getCoachCopyKey() : coachCopyKey,
			raid.getCalculationVersion(), snapshot == null ? "INSUFFICIENT" : raid.getDataState(),
			snapshot == null ? null : raid.getLastSyncedAt());
	}

	private GoalDtos.FinancialStatsView financialStats(SyntheticFinancialSnapshot snapshot) {
		return new GoalDtos.FinancialStatsView(snapshot.getSpendingBps(), snapshot.getSavingBps(), snapshot.getInvestmentJudgmentBps());
	}
}
