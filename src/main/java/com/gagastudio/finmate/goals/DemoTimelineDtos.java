package com.gagastudio.finmate.goals;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

final class DemoTimelineDtos {
	private DemoTimelineDtos() {
	}
	record AdvanceRequest(@NotBlank @Pattern(regexp = "EUROPE_TRAVEL_JANUARY") String fixtureId,
		@Min(0) @Max(3) int expectedStage) {
	}
	record View(String fixtureId, int stage, GoalDtos.UserGoalView mainGoal, GoalDtos.RaidView raid,
		SyntheticGroupView syntheticGroup) {
	}
	record SyntheticGroupView(String groupId, String name, int memberCount, boolean syntheticDemo,
		boolean eligibleForProductionAggregation) {
	}
}
