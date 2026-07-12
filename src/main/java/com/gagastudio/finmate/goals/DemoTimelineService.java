package com.gagastudio.finmate.goals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.quests.QuestService;
import com.gagastudio.finmate.records.RecordService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DemoTimelineService {
	private static final String FIXTURE_ID = "EUROPE_TRAVEL_JANUARY";
	private final DemoFixtureStateRepository states;
	private final DemoTimelineCommandRepository commands;
	private final SyntheticSnapshotIngestionService ingestion;
	private final GoalService goals;
	private final QuestService quests;
	private final RecordService records;
	private final OnboardingCommandLock commandLock;
	private final ObjectMapper objectMapper;
	DemoTimelineService(DemoFixtureStateRepository states, DemoTimelineCommandRepository commands,
		SyntheticSnapshotIngestionService ingestion, GoalService goals, QuestService quests, RecordService records,
		OnboardingCommandLock commandLock, ObjectMapper objectMapper) {
		this.states = states; this.commands = commands; this.ingestion = ingestion; this.goals = goals; this.quests = quests; this.records = records;
		this.commandLock = commandLock; this.objectMapper = objectMapper;
	}

	@Transactional
	JsonNode advance(UUID userId, String fixtureId, int expectedStage, String idempotencyKey) {
		if (!FIXTURE_ID.equals(fixtureId)) throw new InvalidDemoTimelineException("Unsupported demo fixture");
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidDemoTimelineException("Idempotency-Key must be 16 to 128 characters");
		}
		commandLock.lockUser(userId);
		DemoTimelineCommand replay = commands.findByUserIdAndFixtureIdAndIdempotencyKey(userId, fixtureId, idempotencyKey).orElse(null);
		if (replay != null) {
			if (replay.getRequestExpectedStage() != expectedStage) throw new DemoIdempotencyKeyConflictException();
			return originalResponse(replay);
		}
		Instant now = Instant.now();
		DemoFixtureState state = states.findByIdUserIdAndIdFixtureId(userId, fixtureId).orElseGet(() -> states.save(new DemoFixtureState(userId, fixtureId, now)));
		if (state.getStage() != expectedStage || state.getStage() >= 3) throw new DemoTimelineStaleException();
		int nextStage = state.getStage() + 1;
		DemoStageSnapshot snapshot = DemoStageSnapshot.forStage(nextStage);
		SyntheticSnapshotResult result = ingestion.ingest(userId, new SyntheticSnapshotInput(snapshot.amountKrw(), snapshot.spendingBps(),
			snapshot.savingBps(), snapshot.investmentJudgmentBps(), 0, now));
		state.advance(nextStage, now);
		records.appendSyntheticRecalculation(userId, now);
		quests.confirmSyntheticEvidence(userId, now.plusMillis(1));
		DemoTimelineDtos.View view = currentView(userId, nextStage, result, snapshot);
		DemoTimelineCommand command = commands.save(new DemoTimelineCommand(userId, fixtureId, idempotencyKey, expectedStage, nextStage,
			result, snapshot, goals.currentRaid(userId), serialize(view)));
		return originalResponse(command);
	}

	private DemoTimelineDtos.View currentView(UUID userId, int stage, SyntheticSnapshotResult result, DemoStageSnapshot snapshot) {
		GoalDtos.UserGoalView currentGoal = goals.activeGoal(userId);
		GoalDtos.RaidView currentRaid = goals.currentRaid(userId);
		GoalDtos.UserGoalView goal = new GoalDtos.UserGoalView(currentGoal.goalId(), currentGoal.title(), currentGoal.domain(),
			result.currentAmountKrw(), currentGoal.targetAmountKrw(), currentGoal.targetMonth(), currentGoal.state(),
			currentGoal.confirmedAt(), currentGoal.calculationVersion(), currentGoal.dataState(), result.lastSyncedAt());
		GoalDtos.RaidView raid = new GoalDtos.RaidView(currentRaid.raidId(), currentRaid.goalId(), result.stage(),
			result.bossHpBps(), result.highestProgressBps(), new GoalDtos.FinancialStatsView(snapshot.spendingBps(),
				snapshot.savingBps(), snapshot.investmentJudgmentBps()), currentRaid.xp(), currentRaid.coachCopyKey(),
			currentRaid.calculationVersion(), currentRaid.dataState(), result.lastSyncedAt());
		return new DemoTimelineDtos.View(FIXTURE_ID, stage, goal, raid,
			new DemoTimelineDtos.SyntheticGroupView("group-demo-10", "Demo adventurers", 10, true, false));
	}

	private String serialize(DemoTimelineDtos.View view) {
		try {
			return objectMapper.writeValueAsString(view);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Unable to serialize demo timeline response", exception);
		}
	}

	private JsonNode originalResponse(DemoTimelineCommand command) {
		try {
			return objectMapper.readTree(command.getOriginalResponse());
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored demo timeline response is invalid", exception);
		}
	}
}
