package com.gagastudio.finmate.records;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class RecordDtos {
	private RecordDtos() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record DailyActivityView(String activityId, String activityType, String title, Long amountKrw,
		Instant occurredAt, boolean primary, List<String> categoryLabels) {
	}

	record BudgetStatusView(long budgetKrw, long spentKrw, long remainingKrw, int usedBps) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	record DailyRecordView(String date, String status, List<DailyActivityView> activities,
		BudgetStatusView budget, int xpEarned, String reflection, String recalculationSummary,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record DailyRecordPage(List<DailyRecordView> items, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record JourneyNodeView(String date, String status, DailyActivityView primaryActivity,
		List<String> secondaryActivityTypes, int hiddenActivityCount, boolean detailAvailable) {
	}

	record MonthlyMoneySummaryView(long incomeKrw, long expenseKrw, long savingKrw) {
	}

	record DailyJourneyMonthView(String month, int recordedDayCount, int dayCount,
		MonthlyMoneySummaryView moneySummary, List<JourneyNodeView> nodes,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record SaveReflectionRequest(@NotBlank @Size(max = 500) String reflection) {
	}
}
