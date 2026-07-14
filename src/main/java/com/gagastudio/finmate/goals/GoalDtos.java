package com.gagastudio.finmate.goals;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
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

	record ProfileContext(
		@NotBlank @Pattern(regexp = "REGULAR|IRREGULAR|NONE") String incomeRegularity,
		@NotBlank @Pattern(regexp = "WITH_FAMILY|RENT|DORMITORY|OTHER") String housingType,
		@NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String fixedCostBurden) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CompleteOnboardingRequest(
		@NotBlank @Size(max = 30) String displayName,
		@Valid ProfileContext context,
		String moneyConcern,
		String financialTendency,
		String ageBand,
		String occupationGroup,
		@Size(max = 8) List<@Size(max = 30) String> lifestyleTags,
		Boolean anonymousShareConsent,
		Boolean syntheticMyDataConsent,
		String finishMode,
		@Valid MainGoalRequest mainGoal,
		Boolean confirmMainGoal) {

		@AssertTrue(message = "onboarding payload must be EXPLORE_ONLY or a supported legacy goal payload")
		boolean isSupportedPayload() {
			if (isLegacy()) return Boolean.TRUE.equals(confirmMainGoal);
			return context != null
				&& List.of("SPENDING", "SAVING", "EMERGENCY_FUND", "INVESTMENT_JUDGMENT", "UNSURE").contains(moneyConcern)
				&& List.of("CAUTIOUS", "BALANCED", "EXPLORING").contains(financialTendency)
				&& lifestyleTags != null
				&& anonymousShareConsent != null
				&& Boolean.TRUE.equals(syntheticMyDataConsent)
				&& "EXPLORE_ONLY".equals(finishMode);
		}

		boolean isLegacy() {
			return mainGoal != null;
		}

		ProfileContext resolvedContext() {
			return context == null ? new ProfileContext("REGULAR", "RENT", "MEDIUM") : context;
		}

		String resolvedMoneyConcern() {
			return moneyConcern == null ? "SAVING" : moneyConcern;
		}

		String resolvedFinancialTendency() {
			return financialTendency == null ? "BALANCED" : financialTendency;
		}

		List<String> resolvedLifestyleTags() {
			return lifestyleTags == null ? List.of() : lifestyleTags;
		}

		String resolvedAgeBand() {
			return ageBand == null || ageBand.isBlank() ? "UNKNOWN" : ageBand;
		}

		String resolvedOccupationGroup() {
			return occupationGroup == null || occupationGroup.isBlank() ? "UNKNOWN" : occupationGroup;
		}
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

	record ConfirmUserGoalRequest(@NotNull @Valid MainGoalRequest goal, @NotNull @AssertTrue Boolean confirm) {
	}

	record BaselineSummary(long disposableIncomeKrw, int spendingRateBps, int savingRateBps,
		int investmentJudgmentBps) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record OnboardingView(String status, String onboardingState, String displayName, ProfileContext context,
		BaselineSummary baseline, UserGoalView mainGoal, String calculationVersion, String dataState,
		@JsonInclude(JsonInclude.Include.ALWAYS) Instant lastSyncedAt) {
	}

	record UserGoalView(String goalId, String title, String domain, long currentAmountKrw, long targetAmountKrw,
		String targetMonth, String state, Instant confirmedAt, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record FinancialStatsView(
		@Min(0) @Max(10_000) int spendingDefenseBps,
		@Min(0) @Max(10_000) int savingHpBps,
		@Min(0) @Max(10_000) int investmentJudgmentBps,
		@Min(0) int questXp) {
	}

	record RaidView(String raidId, String goalId, int stage, int bossHpBps, int currentProgressBps,
		int highestProgressBps, String status, FinancialStatsView financialStats, String coachCopyKey,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record HomeView(String mode, long totalAssetsKrw, UserGoalView mainGoal, RaidView raid,
		FinancialStatsView financialStats, Object activeRoutineBuild, Object nextQuest, List<String> lockedActions,
		String calculationVersion, String dataState,
		@JsonInclude(JsonInclude.Include.ALWAYS) Instant lastSyncedAt) {
	}

	record CharacterMetric(String label, String displayValue, String reasonCopyKey) {
	}

	record TrendPoint(String date, int value) {
	}

	record CharacterReportView(String reportType, String characterName, int scoreBps,
		List<CharacterMetric> metrics, List<TrendPoint> trend30Days, String nextQuestId,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record MonthlyReportView(String month, int goalProgressBps, FinancialStatsView financialStats, int xpEarned,
		int completedQuestCount, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
}
