package com.gagastudio.finmate.goals;

import com.gagastudio.finmate.auth.UserOnboardingStatusService;
import com.gagastudio.finmate.mate.MateService;
import com.gagastudio.finmate.quests.QuestService;
import com.gagastudio.finmate.runtime.RuntimeFinancialSummary;
import com.gagastudio.finmate.runtime.RuntimeGoalSnapshot;
import com.gagastudio.finmate.runtime.RuntimeFinancialStats;
import com.gagastudio.finmate.runtime.RuntimeSavingDelta;
import com.gagastudio.finmate.runtime.SyntheticRuntimeReadService;
import java.time.Instant;
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
	private final SyntheticRuntimeReadService runtimeReads;

	GoalService(OnboardingStateRepository onboardingStates, UserGoalRepository goals,
		SyntheticFinancialSnapshotRepository snapshots, RaidProjectionRepository raids, GoalValidator validator,
		OnboardingCommandLock commandLock, GoalCommandStore goalCommands,
		SyntheticSnapshotIngestionService snapshotIngestion, MateService mateService,
		QuestService questService, UserOnboardingStatusService onboardingStatusService,
		SyntheticPersonaBindingService syntheticPersonaBindings, SyntheticRuntimeReadService runtimeReads) {
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
		this.runtimeReads = runtimeReads;
	}

	GoalDtos.OnboardingView onboarding(UUID userId) {
		OnboardingState state = onboardingStates.findById(userId).orElse(null);
		if (state == null) {
			return new GoalDtos.OnboardingView("IN_PROGRESS", "EXPLORE_ONLY", null, null,
				baseline(userId, runtimeReads.latestMetrics(userId)), null,
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
		onboardingStatusService.complete(userId, request.displayName().trim(), state.isAnonymousShareConsent());
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
		RuntimeFinancialSummary runtime = runtimeReads.latestMetrics(userId);
		RuntimeFinancialStats runtimeStats = runtimeReads.financialStats(userId);
		UserGoal goal = goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES).orElse(null);
		if (goal == null) {
			return new GoalDtos.HomeView("EXPLORE_ONLY", null, null, null,
				financialStats(userId, runtimeStats), null, null, EXPLORE_LOCKS, "home-calc-v2", runtime.dataState(),
				runtime.lastSyncedAt());
		}
		refreshRuntimeGoal(userId, goal);
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		RaidProjection raid = raid(userId, goal);
		GoalDtos.FinancialStatsView stats = snapshot == null ? financialStats(userId, runtimeStats) : financialStats(userId, snapshot);
		return new GoalDtos.HomeView("GOAL_ACTIVE", null, goalView(goal),
			raidView(userId, raid, snapshot, runtime, "HOME_GOAL_CONFIRMED_V2"), stats,
			mateService.activeBuildForHome(userId), null, List.of(), "home-calc-v2",
			snapshot == null ? "INSUFFICIENT" : "FRESH", snapshot == null ? null : snapshot.getLastSyncedAt());
	}

	GoalDtos.RaidView currentRaid(UUID userId) {
		UserGoal goal = currentGoalEntity(userId);
		refreshRuntimeGoal(userId, goal);
		return raidView(userId, raid(userId, goal), latestSnapshot(userId, goal.getId()),
			runtimeReads.latestMetrics(userId), null);
	}

	GoalDtos.CharacterReportView characterReport(UUID userId, String reportType) {
		UserGoal goal = currentGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		RuntimeFinancialSummary runtime = runtimeReads.latestMetrics(userId);
		RuntimeFinancialStats runtimeStats = runtimeReads.financialStats(userId);
		GoalDtos.FinancialStatsView stats = snapshot == null ? financialStats(userId, runtimeStats) : financialStats(userId, snapshot);
		String character;
		Integer score;
		List<GoalDtos.CharacterMetric> metrics;
		switch (reportType) {
			case "SPENDING_DEFENSE" -> {
				character = "BEAR";
				score = stats.spendingDefenseBps();
				metrics = metric("소비율", score, "SPENDING_RATE_FROM_DISPOSABLE_INCOME_V1");
			}
			case "SAVING_HP" -> {
				character = "SEAL";
				score = stats.savingHpBps();
				metrics = metric("저축률", score, "SAVING_RATE_FROM_DISPOSABLE_INCOME_V1");
			}
			case "INVESTMENT_JUDGMENT" -> {
				character = "RABBIT";
				score = stats.investmentJudgmentBps();
				metrics = metric("투자 점검", score, "INVESTMENT_JUDGMENT_CHECK_V1");
			}
			case "QUEST_XP" -> {
				character = "BIRD";
				score = Math.min(10_000, stats.questXp() * 100);
				metrics = List.of(new GoalDtos.CharacterMetric("퀘스트 XP", Integer.toString(stats.questXp()), "QUEST_XP_EARNED_V1"));
			}
			default -> throw new InvalidMainGoalException("Unknown character report type");
		}
		List<GoalDtos.TrendPoint> trend = "QUEST_XP".equals(reportType)
			? List.of() : financialTrend(userId, goal.getId(), reportType);
		Instant lastSyncedAt = snapshot == null ? runtime.lastSyncedAt() : snapshot.getLastSyncedAt();
		String dataState = "QUEST_XP".equals(reportType) || score != null ? "FRESH" : "INSUFFICIENT";
		return new GoalDtos.CharacterReportView(reportType, character, score, metrics, trend,
			questService.recommendedQuestId(userId, reportType), "character-report-v1", dataState, lastSyncedAt);
	}

	GoalDtos.MonthlyReportView monthlyReport(UUID userId, String month) {
		YearMonth requestedMonth;
		try {
			requestedMonth = YearMonth.parse(month);
		} catch (RuntimeException exception) {
			throw new InvalidReportMonthException();
		}
		ZoneId seoul = ZoneId.of("Asia/Seoul");
		Instant monthStart = requestedMonth.atDay(1).atStartOfDay(seoul).toInstant();
		Instant nextMonthStart = requestedMonth.plusMonths(1).atDay(1).atStartOfDay(seoul).toInstant();
		int monthlyXp = questService.totalXp(userId, monthStart, nextMonthStart);
		int monthlyCompletedCount = questService.completedCount(userId, monthStart, nextMonthStart);
		UserGoal goal = currentGoalEntity(userId);
		SyntheticFinancialSnapshot snapshot = snapshots
			.findTopByUserIdAndGoalIdAndSnapshotMonthOrderByLastSyncedAtDesc(userId, goal.getId(), requestedMonth.atDay(1))
			.orElse(null);
		if (snapshot == null) {
			RuntimeFinancialSummary runtime = runtimeReads.metrics(userId, requestedMonth);
			return new GoalDtos.MonthlyReportView(month, 0,
				financialStats(userId, runtimeReads.financialStats(userId, requestedMonth)), monthlyXp,
				monthlyCompletedCount,
				"report-calc-v2", "INSUFFICIENT", runtime.lastSyncedAt());
		}
		RaidProjection raid = raid(userId, goal);
		int progress = GoalProgress.normalizedBps(raid.getConfirmedBaselineAmountKrw(), goal.getTargetAmountKrw(),
			snapshot.toData().observedGoalAmountKrw());
		return new GoalDtos.MonthlyReportView(month, progress, financialStats(userId, snapshot), monthlyXp,
			monthlyCompletedCount,
			"report-calc-v2", "FRESH", snapshot.getLastSyncedAt());
	}

	private GoalDtos.OnboardingView onboardingView(UUID userId, OnboardingState state) {
		UserGoal goal = goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES).orElse(null);
		RuntimeFinancialSummary runtime = runtimeReads.latestMetrics(userId);
		return new GoalDtos.OnboardingView(state.getStatus(), goal == null ? "EXPLORE_ONLY" : "GOAL_ACTIVE",
			state.getDisplayName(), state.context(), baseline(userId, runtime), goal == null ? null : goalView(goal),
			"baseline-calc-v2", runtime.dataState(), runtime.lastSyncedAt());
	}

	private UserGoal createGoalAndBaseline(UUID userId, GoalDraft draft, Instant now) {
		UserGoal goal = goals.saveAndFlush(new UserGoal(userId, draft, now, now));
		RuntimeFinancialSummary runtime = runtimeReads.latestMetrics(userId);
		RuntimeFinancialStats stats = runtimeReads.financialStats(userId);
		if (runtime.isFresh() && stats.spendingDefenseBps() != null && stats.savingHpBps() != null
			&& stats.investmentJudgmentBps() != null) {
			snapshotIngestion.ingest(userId, new SyntheticSnapshotInput(
				goal.getCurrentAmountKrw(), stats.spendingDefenseBps(), stats.savingHpBps(),
				stats.investmentJudgmentBps(), 0, runtime.lastSyncedAt()));
		} else {
			raids.save(new RaidProjection(userId, goal, now));
		}
		return goal;
	}

	private UserGoal currentGoalEntity(UUID userId) {
		return goals.findFirstByUserIdAndStateInOrderByConfirmedAtDesc(userId, CURRENT_GOAL_STATES)
			.orElseThrow(MainGoalNotFoundException::new);
	}

	private void refreshRuntimeGoal(UUID userId, UserGoal goal) {
		if (!"ACTIVE".equals(goal.getState()) || !"SAVING".equals(goal.getDomain())) return;
		SyntheticFinancialSnapshot snapshot = latestSnapshot(userId, goal.getId());
		if (snapshot == null) return;
		RuntimeSavingDelta delta = runtimeReads.savingDeltaAfter(userId, snapshot.getLastSyncedAt()).orElse(null);
		if (delta == null) return;
		RuntimeFinancialStats stats = runtimeReads.financialStats(userId,
			YearMonth.from(delta.lastSyncedAt().atZone(ZoneId.of("Asia/Seoul"))));
		if (stats.spendingDefenseBps() == null || stats.savingHpBps() == null
			|| stats.investmentJudgmentBps() == null) return;
		long observedAmount = Math.max(0, goal.getCurrentAmountKrw() + delta.netInflowKrw());
		snapshotIngestion.ingest(userId, new SyntheticSnapshotInput(observedAmount,
			stats.spendingDefenseBps(), stats.savingHpBps(), stats.investmentJudgmentBps(),
			0, delta.lastSyncedAt()));
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

	private GoalDtos.UserGoalView goalView(UserGoal goal) {
		return new GoalDtos.UserGoalView(goal.getId().toString(), goal.getTitle(), goal.getDomain(), goal.getCurrentAmountKrw(),
			goal.getTargetAmountKrw(), YearMonth.from(goal.getTargetMonth()).toString(), goal.getState(), goal.getConfirmedAt(),
			goal.getCalculationVersion(), goal.getDataState(), goal.getLastSyncedAt());
	}

	private GoalDtos.RaidView raidView(UUID userId, RaidProjection raid, SyntheticFinancialSnapshot snapshot,
		RuntimeFinancialSummary runtime, String coachCopyKey) {
		GoalDtos.FinancialStatsView stats = snapshot == null
			? financialStats(userId, runtimeReads.financialStats(userId)) : financialStats(userId, snapshot);
		String status = raid.getHighestProgressBps() >= 10_000 ? "COMPLETED"
			: raid.getCurrentProgressBps() == 0 ? "WAITING_FOR_DATA" : "ACTIVE";
		return new GoalDtos.RaidView(raid.getId().toString(), raid.getGoalId().toString(), raid.getStage(),
			raid.getBossHpBps(), raid.getCurrentProgressBps(), raid.getHighestProgressBps(), status, stats,
			coachCopyKey == null ? raid.getCoachCopyKey() : coachCopyKey, raid.getCalculationVersion(),
			snapshot == null ? "INSUFFICIENT" : raid.getDataState(), snapshot == null ? null : raid.getLastSyncedAt());
	}

	private GoalDtos.BaselineSummary baseline(UUID userId, RuntimeFinancialSummary runtime) {
		RuntimeFinancialStats stats = runtimeReads.financialStats(userId);
		return new GoalDtos.BaselineSummary(runtime.disposableIncomeKrw(), runtime.consumptionRateBps(),
			runtime.savingRateBps(), stats.investmentJudgmentBps());
	}

	private GoalDtos.FinancialStatsView financialStats(UUID userId, RuntimeFinancialStats stats) {
		return new GoalDtos.FinancialStatsView(stats.spendingDefenseBps(), stats.savingHpBps(),
			stats.investmentJudgmentBps(), stats.questXp() + questService.totalXp(userId));
	}

	private GoalDtos.FinancialStatsView financialStats(UUID userId, SyntheticFinancialSnapshot snapshot) {
		RuntimeFinancialStats runtimeStats = runtimeReads.financialStats(userId);
		return new GoalDtos.FinancialStatsView(snapshot.getSpendingBps(), snapshot.getSavingBps(),
			snapshot.getInvestmentJudgmentBps(), runtimeStats.questXp() + questService.totalXp(userId));
	}

	private List<GoalDtos.CharacterMetric> metric(String label, Integer score, String reasonCopyKey) {
		return score == null ? List.of()
			: List.of(new GoalDtos.CharacterMetric(label, percent(score), reasonCopyKey));
	}

	private List<GoalDtos.TrendPoint> financialTrend(UUID userId, UUID goalId, String reportType) {
		return runtimeReads.latestGoalSnapshots(userId, goalId, 2).stream()
			.map(snapshot -> new GoalDtos.TrendPoint(
				snapshot.lastSyncedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString(),
				trendValue(snapshot, reportType)))
			.toList();
	}

	private int trendValue(RuntimeGoalSnapshot snapshot, String reportType) {
		return switch (reportType) {
			case "SPENDING_DEFENSE" -> snapshot.spendingRateBps();
			case "SAVING_HP" -> snapshot.savingRateBps();
			case "INVESTMENT_JUDGMENT" -> snapshot.investmentRateBps();
			default -> throw new InvalidMainGoalException("Unknown character report type");
		};
	}

	private String percent(int basisPoints) {
		return (basisPoints / 100.0) + "%";
	}
}
