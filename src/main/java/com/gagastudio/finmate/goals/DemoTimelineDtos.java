package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

final class DemoTimelineDtos {
	private DemoTimelineDtos() {
	}
	record AdvanceRequest(@NotBlank @Pattern(regexp = "EUROPE_TRAVEL_JANUARY") String fixtureId,
		@NotNull @Min(0) @Max(5) Integer expectedFrameIndex) {
	}
	record Frame(int frameIndex, String month, long savingEventKrw, long goalCurrentAmountKrw,
		int goalProgressBps, String dataState) {
	}
	record View(String fixtureId, long initialGoalAmountKrw, long targetGoalAmountKrw, int currentFrameIndex,
		List<Frame> frames, GoalDtos.UserGoalView mainGoal, GoalDtos.RaidView raid,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
}
