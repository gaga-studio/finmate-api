package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

final class MateDtos {
	private MateDtos() {
	}

	record MateGroupPage(List<MateGroupView> items) {
	}

	record MateGroupView(String groupId, String name, int memberCount, boolean syntheticDemo,
		boolean eligibleForProductionAggregation) {
	}

	record AdventurerPage(String groupId, List<AdventurerView> items, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record AdventurerView(String adventurerId, String groupId, String alias, List<String> similarityReasons,
		List<RoutineSummary> routines, Instant approvedAt) {
	}

	record RoutineSummary(String routineId, String title, List<String> availableDomains) {
	}

	record RoutineView(String routineId, String adventurerId, String groupId, String title, String description,
		List<String> availableDomains, int maintainedDays) {
	}

	record CreateAdaptationRequest(@NotBlank String groupId, @NotBlank String adventurerId, @NotBlank String routineId) {
	}

	record AdaptationAwaitingView(String adaptationId, String sourceRoutineId, String state, List<String> availableDomains,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record ChooseDomainRequest(@NotBlank String domain) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CandidateView(String candidateId, String difficulty, String domain, String title, String targetKind,
		Long targetAmountKrw, Integer targetRatioBps, String behaviorTarget, List<String> steps) {
	}

	record AdaptationSetView(String adaptationId, String sourceRoutineId, String state, String selectedDomain,
		CandidateView light, CandidateView standard, CandidateView challenge, String calculationVersion, String dataState,
		Instant lastSyncedAt) {
	}

	record ActiveBuildView(String buildId, String candidateId, String sourceRoutineId, String domain, String difficulty,
		String status, List<String> steps, Instant activatedAt, Instant archivedAt, String replacesBuildId,
		String replacedByBuildId, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}

	record ReplaceBuildRequest(@NotBlank String adaptationId, @NotBlank String candidateId,
		@NotNull @AssertTrue Boolean confirmReplacement) {
	}

	record ReplacementView(ActiveBuildView archivedBuild, ActiveBuildView activeBuild, Instant replacedAt) {
	}
}
