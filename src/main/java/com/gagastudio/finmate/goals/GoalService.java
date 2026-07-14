package com.gagastudio.finmate.goals;

import com.gagastudio.finmate.auth.UserOnboardingStatusService;
import com.gagastudio.finmate.mate.MateService;
import com.gagastudio.finmate.quests.QuestService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GoalService {
	private static final String ACTIVE = "ACTIVE";
	private static final List<String> CURRENT_GOAL_STATES = List.of(ACTIVE, "COMPLETED");
	private static final long TOTAL_ASSETS_KRW = 4_280_000L;
	private static final GoalDtos.BaselineSummary BASELINE =
		new GoalDtos.BaselineSummary(1_100_000L, 5_200, 1_800, 4_000);
	private static final List<String> EXPLORE_LOCKS =
		List.of("RAID", "QUEST_ACCEPT", "ROUTINE_IMPORT", "PERSONALIZED_PRODUCT_INFO");
	private final OnboardingStateRepository onboardingStates;
	private final UserGoalRepository goals;
	private final SyntheticFinancialSnapshotRepository snapshots;
	private final RaidProjectionRepository raids;
	private final GoalValidator validator;
	private final OnboardingCommandLock commandLock;
	private final GoalCommandStore goalCommands;
	private final SyntheticSnapshotIngestionService snapshotIngestion;
	private final MateService mateService;
	private final QuestService questService;
	private final UserOnboardingStatusService onboardingStatusService;
	private final SyntheticPersonaBindingService syntheticPersonaBindings;

	GoalService(OnboardingStateRepository onboardingStates, UserGoalRepository goals,
		SyntheticFinancialSnapshotRepository snapshots, RaidProjectionRepository raids, GoalValidator validator,
		OnboardingCommandLock commandLock, GoalCommandStore goalCommands,
		SyntheticSnapshotIngestionService snapshotIngestion, MateService mateService,
		QuestService questService, UserOnboardingStatusService onboardingStatusService,
		SyntheticPersonaBindingService syntheticPersonaBindings) {
		this.onboardingStates = onboardingStates;
		this.goals = goals;
		this.snapshots = snapshots;
		this.raids = raids;
		this.validator = validator;
		this.commandLock = commandLock;
		this.goalCommands = goalCommands;
		this.snapshotIngestion = snapshotIngestion;
		this.mateService = mateService;
		this.questService = questService;
		this.onboardingStatusService = onboardingStatusService;
		this.syntheticPersonaBindings = syntheticPersonaBindings;
	}

	GoalDtos.OnboardingView onboarding(UUID userId) {
		OnboardingState state = onboardingStates.findById(userId).orElse(null);
		if (state == null) {
			return new GoalDtos.OnboardingView("IN_PROGRESS", "EXPLORE_ONLY", null, null, BASELINE, null,
				"baseline-calc-v2", "INSUFFICIENT", null);
		}
		return onboardingView(userId, state);
	}

	@Transactional
	GoalDtos.OnboardingView completeOnboarding(UUID userId, String idempotencyKey,
		GoalDtos.CompleteOnboardingRequest request) {
		validateIdempotencyKey(idempotencyKey);
		commandLock.lockUser(userId);
		OnboardingState existingState = onboardingStates.findById(userId).orElse(null);
		if (existingState != null) {
			if (existingState.hasIdempotencyKey(idempotencyKey)) return onboardingView(userId, existingState);
			throw new ActiveMainGoalException();
		}

		Instant now = Instant.now();
		OnboardingState state = onboardingStates.save(new OnboardingState(
			userId, request.displayName().trim(), now, idempotencyKey, request));
		onboardingStatusService.complete(userId, request.displayName().trim());
		if (state.isSyntheticMyDataConsent()) syntheticPersonaBindings.bindIfEligible(userId, state);

		if (request.isLegacy()) {
			GoalDraft draft = request.mainGoal().toDraft();
			validator.validate(draft, request.confirmMainGoal());
			createGoalAndBaseline(userId, draft, now);
		}
		return onboardingView(userId, state);
	}

	@Transactional
	GoalDtos.UserGoalView confirmGoal(UUID userId, String idempotencyKey,
		GoalDtos.ConfirmUserGoalRequest request) {
		validateIdempotencyKey(idempotencyKey);
		GoalDraft draft = request.goal().toDraft();
		validator.validate(draft, request.confirm());
		commandLock.lockUser(userId);
		if (onboardingStates.findById(userId).isEmpty()) {
			throw new InvalidMainGoalException("Complete onboarding before confirming a goal");
		}
		String fingerprint = fingerprint(draft);
		GoalCommandStore.StoredGoalCommand replay = goalCommands.find(userId, idempotencyKey).orElse(null);
		if (replay != null) {
			if (!replay.fingerprint().equals(fingerprint)) {
				throw new InvalidMainGoalException("Idempotency-Key was already used with another goal");
			}
			return goalView(goals.findByIdAndUserId(replay.goalId(), userId).orElseThrow(MainGoalNotFoundException::new));
		}
		if (goals.findByUserIdAndState(userId, ACTIVE).isPresent()) throw new ActiveMainGoalException();

		Instant now = Instant.now();
		UserGoal goal = createGoalAndBaseline(userId, draft, now);
		goalCommands.save(userId, idempotencyKey, fingerprint, goal.getId(), now);
		return goalView(goal);
	}

	GoalDtos.UserGoalView activeGoal(UUID userId) {
		return goalView(currentGoalEntity(userId));
	}

	GoalDtos.HomeView home(UUID userId) {
		UserGoal goal = goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES).orElse(null);
		if (goal == null) {
			return new GoalDtos.HomeView("EXPLORE_ONLY", TOTAL_ASSETS_KRW, null, null,
				baselineStats(), null, null, EXPLORE_LOCKS, "home-calc-v2", "FRESH", onboardingSyncedAt(userId));
		}
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		RaidProjection raid = raid(userId, goal);
		GoalDtos.FinancialStatsView stats = snapshot == null ? baselineStats(userId) : financialStats(userId, snapshot);
		return new GoalDtos.HomeView("GOAL_ACTIVE", TOTAL_ASSETS_KRW, goalView(goal),
			raidView(userId, raid, snapshot, "HOME_GOAL_CONFIRMED_V2"), stats, mateService.activeBuildForHome(userId), null,
			List.of(), "home-calc-v2", snapshot == null ? "INSUFFICIENT" : "FRESH",
			snapshot == null ? null : snapshot.getLastSyncedAt());
	}

	GoalDtos.RaidView currentRaid(UUID userId) {
		UserGoal goal = currentGoalEntity(userId);
		return raidView(userId, raid(userId, goal), latestSnapshot(userId, goal.getId()), null);
	}

	GoalDtos.CharacterReportView characterReport(UUID userId, String reportType) {
		UserGoal goal = currentGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		if (snapshot == null) throw new MainGoalNotFoundException();
		GoalDtos.FinancialStatsView stats = financialStats(userId, snapshot);
		String character;
		int score;
		List<GoalDtos.CharacterMetric> metrics;
		switch (reportType) {
			case "SPENDING_DEFENSE" -> {
				character = "BEAR";
				score = stats.spendingDefenseBps();
				metrics = List.of(new GoalDtos.CharacterMetric("소비율", percent(score), "SPENDING_RATE_FROM_DISPOSABLE_INCOME_V1"));
			}
			case "SAVING_HP" -> {
				character = "SEAL";
				score = stats.savingHpBps();
				metrics = List.of(
					new GoalDtos.CharacterMetric("저축률", percent(score), "SAVING_RATE_FROM_DISPOSABLE_INCOME_V1"),
					new GoalDtos.CharacterMetric("비상금", "42만원", "EMERGENCY_FUND_CURRENT_V1"));
			}
			case "INVESTMENT_JUDGMENT" -> {
				character = "RABBIT";
				score = stats.investmentJudgmentBps();
				metrics = List.of(new GoalDtos.CharacterMetric("투자 점검", percent(score), "INVESTMENT_JUDGMENT_CHECK_V1"));
			}
			case "QUEST_XP" -> {
				character = "BIRD";
				score = Math.min(10_000, stats.questXp() * 100);
				metrics = List.of(new GoalDtos.CharacterMetric("퀘스트 XP", Integer.toString(stats.questXp()), "QUEST_XP_EARNED_V1"));
			}
			default -> throw new InvalidMainGoalException("Unknown character report type");
		}
		LocalDate end = snapshot.getLastSyncedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
		List<GoalDtos.TrendPoint> trend = List.of(
			new GoalDtos.TrendPoint(end.minusDays(29).toString(), Math.max(0, score - 300)),
			new GoalDtos.TrendPoint(end.toString(), score));
		return new GoalDtos.CharacterReportView(reportType, character, score, metrics, trend,
			questService.recommendedQuestId(userId, reportType), "character-report-v1", "FRESH",
			snapshot.getLastSyncedAt());
	}

	GoalDtos.MonthlyReportView monthlyReport(UUID userId, String month) {
		YearMonth requestedMonth;
		try {
			requestedMonth = YearMonth.parse(month);
		} catch (RuntimeException exception) {
			throw new InvalidReportMonthException();
		}
		UserGoal goal = currentGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = snapshots
			.findTopByUserIdAndGoalIdAndSnapshotMonthOrderByLastSyncedAtDesc(userId, goal.getId(), requestedMonth.atDay(1))
			.orElse(null);
		if (snapshot == null) {
			return new GoalDtos.MonthlyReportView(month, 0, baselineStats(userId), questService.totalXp(userId),
				questService.completedCount(userId),
				"report-calc-v2", "INSUFFICIENT", null);
		}
		RaidProjection raid = raid(userId, goal);
		int progress = GoalProgress.normalizedBps(raid.getConfirmedBaselineAmountKrw(), goal.getTargetAmountKrw(),
			snapshot.toData().observedGoalAmountKrw());
		return new GoalDtos.MonthlyReportView(month, progress, financialStats(userId, snapshot), questService.totalXp(userId),
			questService.completedCount(userId),
			"report-calc-v2", "FRESH", snapshot.getLastSyncedAt());
	}

	private GoalDtos.OnboardingView onboardingView(UUID userId, OnboardingState state) {
		UserGoal goal = goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES).orElse(null);
		return new GoalDtos.OnboardingView(state.getStatus(), goal == null ? "EXPLORE_ONLY" : "GOAL_ACTIVE",
			state.getDisplayName(), state.context(), BASELINE, goal == null ? null : goalView(goal),
			"baseline-calc-v2", "FRESH", state.getLastSyncedAt());
	}

	private UserGoal createGoalAndBaseline(UUID userId, GoalDraft draft, Instant now) {
		UserGoal goal = goals.saveAndFlush(new UserGoal(userId, draft, now, now));
		snapshotIngestion.ingest(userId, new SyntheticSnapshotInput(
			goal.getCurrentAmountKrw(), BASELINE.spendingRateBps(), BASELINE.savingRateBps(),
			BASELINE.investmentJudgmentBps(), 0, now));
		return goal;
	}

	private UserGoal currentGoalEntity(UUID userId) {
		return goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES)
			.orElseThrow(MainGoalNotFoundException::new);
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

	private String fingerprint(GoalDraft draft) {
		return String.join("|", draft.title(), draft.domain(), Long.toString(draft.currentAmountKrw()),
			Long.toString(draft.targetAmountKrw()), draft.targetMonth().toString());
	}

	private Instant onboardingSyncedAt(UUID userId) {
		return onboardingStates.findById(userId).map(OnboardingState::getLastSyncedAt).orElse(null);
	}

	private GoalDtos.UserGoalView goalView(UserGoal goal) {
		return new GoalDtos.UserGoalView(goal.getId().toString(), goal.getTitle(), goal.getDomain(), goal.getCurrentAmountKrw(),
			goal.getTargetAmountKrw(), YearMonth.from(goal.getTargetMonth()).toString(), goal.getState(), goal.getConfirmedAt(),
			goal.getCalculationVersion(), goal.getDataState(), goal.getLastSyncedAt());
	}

	private GoalDtos.RaidView raidView(UUID userId, RaidProjection raid, SyntheticFinancialSnapshot snapshot,
		String coachCopyKey) {
		GoalDtos.FinancialStatsView stats = snapshot == null ? baselineStats(userId) : financialStats(userId, snapshot);
		String status = raid.getHighestProgressBps() >= 10_000 ? "COMPLETED"
			: raid.getCurrentProgressBps() == 0 ? "WAITING_FOR_DATA" : "ACTIVE";
		return new GoalDtos.RaidView(raid.getId().toString(), raid.getGoalId().toString(), raid.getStage(),
			raid.getBossHpBps(), raid.getCurrentProgressBps(), raid.getHighestProgressBps(), status, stats,
			coachCopyKey == null ? raid.getCoachCopyKey() : coachCopyKey, raid.getCalculationVersion(),
			snapshot == null ? "INSUFFICIENT" : raid.getDataState(), snapshot == null ? null : raid.getLastSyncedAt());
	}

	private GoalDtos.FinancialStatsView baselineStats() {
		return new GoalDtos.FinancialStatsView(BASELINE.spendingRateBps(), BASELINE.savingRateBps(),
			BASELINE.investmentJudgmentBps(), 0);
	}

	private GoalDtos.FinancialStatsView baselineStats(UUID userId) {
		return new GoalDtos.FinancialStatsView(BASELINE.spendingRateBps(), BASELINE.savingRateBps(),
			BASELINE.investmentJudgmentBps(), questService.totalXp(userId));
	}

	private GoalDtos.FinancialStatsView financialStats(UUID userId, SyntheticFinancialSnapshot snapshot) {
		return new GoalDtos.FinancialStatsView(snapshot.getSpendingBps(), snapshot.getSavingBps(),
			snapshot.getInvestmentJudgmentBps(), questService.totalXp(userId));
	}

	private String percent(int basisPoints) {
		return (basisPoints / 100.0) + "%";
	}
}
