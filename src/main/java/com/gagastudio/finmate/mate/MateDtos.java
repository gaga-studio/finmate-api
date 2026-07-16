package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gagastudio.finmate.goals.GoalDtosBridge;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

final class MateDtos {
	private MateDtos() {
	}

	record MateGroupPage(List<MateGroupView> items) {
	}

	record MateGroupView(String groupId, String name, int memberCount, boolean syntheticDemo,
		boolean eligibleForProductionAggregation) {
	}

	record DistributionRange(int p25Bps, int medianBps, int p75Bps) {
	}

	record MateGroupReportView(MateGroupView group, List<String> selectionReasons,
		DistributionRange spendingRateRange, DistributionRange savingRateRange,
		GoalDtosBridge.FinancialStats averageStats, int achieverCount,
		List<AdventurerView> adventurerPreview, List<String> coachCopyKeys,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record AdventurerPage(String groupId, List<AdventurerView> items, String calculationVersion,
		String dataState, Instant lastSyncedAt) {
	}

	record AdventurerView(String adventurerId, String groupId, String alias, List<String> contextTags,
		List<String> similarityReasons, String goalAchievementLabel, List<RoutineSummary> routines,
		Instant verifiedAt, Instant approvedAt) {
	}

	record RoutineSummary(String routineId, String title, String domain, int maintenanceDays) {
	}

	record ComparisonMetric(String label, String myRange, String adventurerRange,
		String interpretationCopyKey) {
	}

	record AdventurerReportView(AdventurerView adventurer, List<ComparisonMetric> comparisonMetrics,
		List<String> routineEvidence, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record RoutineView(String routineId, String adventurerId, String groupId, String title, String domain,
		int maintenanceDays, List<String> steps, List<String> evidenceCopyKeys) {
	}

	record MateExploreSearchRequest(
		@NotBlank @Pattern(regexp = "AGE_19_23|AGE_24_29|AGE_30_34") String ageBand,
		@NotBlank @Pattern(regexp = "STUDENT|EARLY_CAREER|FREELANCER|JOB_SEEKER") String occupationGroup,
		@NotBlank @Pattern(regexp = "NONE|UNDER_200|FROM_200_TO_300|OVER_300") String incomeBand,
		@NotBlank @Pattern(regexp = "PLANNED|BALANCED|VARIABLE") String spendingTendency,
		@NotBlank @Pattern(regexp = "UNDER_10|FROM_10_TO_20|OVER_20") String savingRateBand,
		@NotBlank @Pattern(regexp = "CAUTIOUS|BALANCED|LEARNING") String investmentTendency) {
	}

	record MateExploreSearchPage(List<MateExploreSearchCard> items, int totalEligible, String matchMode,
		List<String> relaxedFilters, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record MateExploreSearchCard(String adventurerId, String groupId, String sourceGroupId, String alias,
		List<String> contextTags, MateExploreRoutineSummary representativeRoutine, int maintenanceDays,
		int similarityScoreBps, List<String> matchedFilters, LocalDate dataAsOf) {
	}

	record MateExploreRoutineSummary(String routineId, String title, String domain) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CreateAdaptationRequest(@NotBlank String groupId, @NotBlank String adventurerId,
		String routineId, String sourceRoutineId, String selectedDomain) {
		@AssertTrue(message = "sourceRoutineId and selectedDomain are required for recommendations")
		boolean hasSupportedShape() {
			return routineId != null || (sourceRoutineId != null && selectedDomain != null);
		}

		boolean isLegacy() { return routineId != null; }
		String resolvedRoutineId() { return routineId == null ? sourceRoutineId : routineId; }
	}

	record AdaptationAwaitingView(String adaptationId, String sourceRoutineId, String state,
		List<String> availableDomains, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record ChooseDomainRequest(@NotBlank String domain) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CandidateView(String candidateId, String difficulty, String domain, String title, String targetKind,
		Long targetAmountKrw, Integer targetBasisPoints, String behaviorTarget, Integer durationDays,
		List<String> steps) {
	}

	record AdaptationSetView(String adaptationId, String sourceRoutineId, String state, String selectedDomain,
		CandidateView light, CandidateView standard, CandidateView challenge, String calculationVersion,
		String dataState, Instant lastSyncedAt) {
	}

	record RoutineRecommendationView(String adaptationId, String sourceRoutineId, String selectedDomain,
		CandidateView recommendedCandidate, String recommendationReasonCopyKey, String relatedProductId,
		List<CandidateView> intensityOptions, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record ActiveBuildView(String buildId, String candidateId, String sourceRoutineId, String domain,
		String difficulty, String status, List<String> steps, Instant activatedAt, Instant archivedAt,
		String replacesBuildId, String replacedByBuildId, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record ReplaceBuildRequest(@NotBlank String adaptationId, @NotBlank String candidateId,
		@NotNull @AssertTrue Boolean confirmReplacement) {
	}

	record ReplacementView(ActiveBuildView archivedBuild, ActiveBuildView activeBuild, Instant replacedAt) {
	}

	record RelatedHanaProductInfoView(String productId, String displayName, String category,
		String relatedRoutineDomain, List<String> keyConditions, List<String> cautions, String informationAsOf,
		String officialInformationUrl, boolean reviewedCatalog, boolean inAppEnrollmentAvailable,
		boolean affectsProgress) {
	}
}
