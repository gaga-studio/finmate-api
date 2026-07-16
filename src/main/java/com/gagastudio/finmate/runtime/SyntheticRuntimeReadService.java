package com.gagastudio.finmate.runtime;

import com.gagastudio.finmate.data.FinancialActivityInput;
import com.gagastudio.finmate.data.FinancialMetricCalculator;
import com.gagastudio.finmate.data.FinancialMetricSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SyntheticRuntimeReadService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final RuntimePersonaBindingRepository bindings;
	private final RuntimeFinancialActivityRepository activities;
	private final RuntimeFeatureProjectionRepository features;
	private final RuntimeGoalSnapshotRepository goalSnapshots;
	private final RuntimeBehaviorRepository behavior;
	private final FinancialMetricCalculator calculator = new FinancialMetricCalculator();

	SyntheticRuntimeReadService(RuntimePersonaBindingRepository bindings,
		RuntimeFinancialActivityRepository activities, RuntimeFeatureProjectionRepository features,
		RuntimeGoalSnapshotRepository goalSnapshots, RuntimeBehaviorRepository behavior) {
		this.bindings = bindings;
		this.activities = activities;
		this.features = features;
		this.goalSnapshots = goalSnapshots;
		this.behavior = behavior;
	}

	public RuntimeFinancialSummary latestMetrics(UUID userId) {
		RuntimePersonaBinding binding = bindings.findByUserId(userId).orElse(null);
		if (binding == null) return RuntimeFinancialSummary.insufficient(null, null);
		Instant latest = activities.findLatestOccurredAt(binding).orElse(null);
		if (latest == null) return RuntimeFinancialSummary.insufficient(null, null);
		return metrics(binding, YearMonth.from(latest.atZone(SEOUL)));
	}

	public RuntimeFinancialSummary metrics(UUID userId, YearMonth targetMonth) {
		return bindings.findByUserId(userId)
			.map(binding -> metrics(binding, targetMonth))
			.orElseGet(() -> RuntimeFinancialSummary.insufficient(targetMonth, null));
	}

	public List<RuntimeFinancialActivity> activities(UUID userId, LocalDate from, LocalDate to) {
		return bindings.findByUserId(userId)
			.map(binding -> activities.findBetween(binding, from.atStartOfDay(SEOUL).toInstant(),
				to.plusDays(1).atStartOfDay(SEOUL).toInstant()))
			.orElseGet(List::of);
	}

	public Optional<RuntimeFeatureProfile> featureProfile(UUID userId) {
		return bindings.findByUserId(userId).flatMap(features::find);
	}

	public RuntimeFinancialStats financialStats(UUID userId) {
		RuntimeFinancialSummary summary = latestMetrics(userId);
		if (summary.month() == null) {
			return new RuntimeFinancialStats(null, null, null, 0, "runtime-financial-stats-v1",
				"INSUFFICIENT", summary.lastSyncedAt());
		}
		return financialStats(userId, summary.month());
	}

	public RuntimeFinancialStats financialStats(UUID userId, YearMonth month) {
		RuntimePersonaBinding binding = bindings.findByUserId(userId).orElse(null);
		RuntimeFinancialSummary summary = metrics(userId, month);
		if (binding == null) {
			return new RuntimeFinancialStats(null, null, null, 0, "runtime-financial-stats-v1",
				"INSUFFICIENT", summary.lastSyncedAt());
		}
		RuntimeBehaviorProfile profile = behavior.findProfile(binding).orElse(null);
		Integer spendingDefense = behavior.budgetAdherenceBps(binding, month);
		Integer investmentJudgment = profile == null ? null : profile.investmentJudgmentBps();
		int questXp = profile == null ? 0 : profile.questXp();
		Instant lastSynced = summary.lastSyncedAt();
		if (profile != null && profile.lastEvidenceDate() != null) {
			Instant behaviorSync = profile.lastEvidenceDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
			if (lastSynced == null || behaviorSync.isAfter(lastSynced)) lastSynced = behaviorSync;
		}
		String state = summary.isFresh() ? "FRESH" : "INSUFFICIENT";
		return new RuntimeFinancialStats(spendingDefense, summary.savingRateBps(), investmentJudgment,
			questXp, "runtime-financial-stats-v1", state, lastSynced);
	}

	public RuntimeBudgetStatus budgetStatus(UUID userId, LocalDate date) {
		return bindings.findByUserId(userId)
			.flatMap(binding -> behavior.budgetStatus(binding, date))
			.orElseGet(RuntimeBudgetStatus::empty);
	}

	public Optional<RuntimeSavingDelta> savingDeltaAfter(UUID userId, Instant afterExclusive) {
		return bindings.findByUserId(userId).flatMap(binding -> activities.savingDeltaAfter(binding, afterExclusive));
	}

	public List<RuntimeGoalSnapshot> latestGoalSnapshots(UUID userId, UUID goalId, int limit) {
		if (limit < 1) return List.of();
		List<RuntimeGoalSnapshot> snapshots = new ArrayList<>(goalSnapshots.findLatestMonths(userId, goalId, limit));
		Collections.reverse(snapshots);
		return List.copyOf(snapshots);
	}

	private RuntimeFinancialSummary metrics(RuntimePersonaBinding binding, YearMonth targetMonth) {
		Instant from = targetMonth.minusMonths(2).atDay(1).atStartOfDay(SEOUL).toInstant();
		Instant to = targetMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
		List<RuntimeFinancialActivity> sourceActivities = activities.findBetween(binding, from, to);
		List<FinancialActivityInput> inputs = sourceActivities.stream()
			.map(activity -> new FinancialActivityInput(activity.activityType(), activity.classification(),
				activity.direction(), activity.amountKrw(), activity.occurredAt()))
			.toList();
		boolean investmentParticipant = features.find(binding)
			.map(profile -> profile.investmentRateBps() != null)
			.orElseGet(() -> sourceActivities.stream().anyMatch(activity -> "INVESTMENT".equals(activity.activityType())));
		FinancialMetricSnapshot snapshot = calculator.calculate(inputs, targetMonth, investmentParticipant);
		if (!"FRESH".equals(snapshot.dataState())) {
			return RuntimeFinancialSummary.insufficient(targetMonth, snapshot.lastSyncedAt());
		}
		return new RuntimeFinancialSummary(targetMonth, snapshot.disposableIncomeKrw(), snapshot.consumptionRateBps(),
			snapshot.savingRateBps(), snapshot.investmentContributionRateBps(), snapshot.calculationVersion(),
			snapshot.dataState(), snapshot.lastSyncedAt());
	}
}
