package com.gagastudio.finmate.goals;

import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class GoalDtos {
	private GoalDtos() {
	}

	record CompleteOnboardingRequest(
		@NotBlank @Size(max = 30) String displayName,
		@NotNull @Valid MainGoalRequest mainGoal,
		@NotNull @AssertTrue Boolean confirmMainGoal) {
	}

	record MainGoalRequest(
		@NotBlank @Size(max = 255) String title,
		@NotBlank @Pattern(regexp = "SPENDING|SAVING") String domain,
		@NotNull @Min(0) Long currentAmountKrw,
		@NotNull @Min(1) Long targetAmountKrw,
		@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String targetMonth) {
		GoalDraft toDraft() {
			return new GoalDraft(title.trim(), domain, currentAmountKrw, targetAmountKrw, java.time.YearMonth.parse(targetMonth));
		}
	}

	record OnboardingView(String status, String displayName, UserGoalView mainGoal) {
	}

	record UserGoalView(String goalId, String title, String domain, long currentAmountKrw, long targetAmountKrw,
		String targetMonth, String state, Instant confirmedAt, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record FinancialStatsView(@Min(0) @Max(10_000) int spendingBps, @Min(0) @Max(10_000) int savingBps,
		@Min(0) @Max(10_000) int investmentJudgmentBps) {
	}

	record RaidView(String raidId, String goalId, int stage, int bossHpBps, int progressBps,
		FinancialStatsView financialStats, int xp, String coachCopyKey, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record HomeView(UserGoalView mainGoal, RaidView raid, Object activeRoutineBuild, Object nextQuest,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record MonthlyReportView(String month, int goalProgressBps, FinancialStatsView financialStats, int xpEarned,
		int completedQuestCount, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
}
