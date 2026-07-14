package com.gagastudio.finmate.goals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.quests.QuestService;
import com.gagastudio.finmate.records.RecordService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DemoTimelineService {
	private static final String FIXTURE_ID = "EUROPE_TRAVEL_JANUARY";
	private static final long INITIAL_GOAL_AMOUNT_KRW = 2_000_000;
	private static final long TARGET_GOAL_AMOUNT_KRW = 5_000_000;
	private static final int FRAME_COUNT = 6;
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
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
	JsonNode advance(UUID userId, String fixtureId, int expectedFrameIndex, String idempotencyKey) {
		if (!FIXTURE_ID.equals(fixtureId)) throw new InvalidDemoTimelineException("Unsupported demo fixture");
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidDemoTimelineException("Idempotency-Key must be 16 to 128 characters");
		}
		commandLock.lockUser(userId);
		DemoTimelineCommand replay = commands.findByUserIdAndFixtureIdAndIdempotencyKey(userId, fixtureId, idempotencyKey).orElse(null);
		if (replay != null) {
			if (replay.getRequestExpectedFrameIndex() != expectedFrameIndex) throw new DemoIdempotencyKeyConflictException();
			return originalResponse(replay);
		}
		Instant now = Instant.now();
		DemoFixtureState state = states.findByIdUserIdAndIdFixtureId(userId, fixtureId).orElseGet(() -> states.save(new DemoFixtureState(userId, fixtureId, now)));
		if (state.getNextFrameIndex() != expectedFrameIndex || state.getNextFrameIndex() >= FRAME_COUNT) {
			throw new DemoTimelineStaleException();
		}
		DemoStageSnapshot snapshot = DemoStageSnapshot.forFrame(expectedFrameIndex);
		Instant frameAt = YearMonth.parse(snapshot.month()).atDay(10).atTime(LocalTime.of(9, 0)).atZone(SEOUL).toInstant();
		SyntheticSnapshotResult result = ingestion.ingest(userId, new SyntheticSnapshotInput(snapshot.amountKrw(), snapshot.spendingBps(),
			snapshot.savingBps(), snapshot.investmentJudgmentBps(), 0, frameAt));
		state.advanceTo(expectedFrameIndex + 1, now);
		records.appendDemoSaving(userId, frameAt, snapshot.savingEventKrw());
		quests.confirmSyntheticEvidence(userId, frameAt.plusMillis(1));
		DemoTimelineDtos.View view = currentView(userId, expectedFrameIndex, result, snapshot);
		DemoTimelineCommand command = commands.save(new DemoTimelineCommand(userId, fixtureId, idempotencyKey,
			expectedFrameIndex, expectedFrameIndex,
			result, snapshot, goals.currentRaid(userId), serialize(view)));
		return originalResponse(command);
	}

	private DemoTimelineDtos.View currentView(UUID userId, int frameIndex, SyntheticSnapshotResult result,
		DemoStageSnapshot snapshot) {
		GoalDtos.UserGoalView currentGoal = goals.activeGoal(userId);
		GoalDtos.RaidView currentRaid = goals.currentRaid(userId);
		GoalDtos.UserGoalView goal = new GoalDtos.UserGoalView(currentGoal.goalId(), currentGoal.title(), currentGoal.domain(),
			result.currentAmountKrw(), currentGoal.targetAmountKrw(), currentGoal.targetMonth(),
			result.currentProgressBps() >= 10_000 ? "COMPLETED" : currentGoal.state(),
			currentGoal.confirmedAt(), currentGoal.calculationVersion(), currentGoal.dataState(), result.lastSyncedAt());
		GoalDtos.RaidView raid = new GoalDtos.RaidView(currentRaid.raidId(), currentRaid.goalId(), result.stage(),
			result.bossHpBps(), result.currentProgressBps(), result.highestProgressBps(),
			result.highestProgressBps() >= 10_000 ? "COMPLETED" : "ACTIVE",
			new GoalDtos.FinancialStatsView(snapshot.spendingBps(), snapshot.savingBps(),
				snapshot.investmentJudgmentBps(), currentRaid.financialStats().questXp()),
			result.highestProgressBps() >= 10_000 ? "EUROPE_TRAVEL_GOAL_COMPLETED_V1" : currentRaid.coachCopyKey(),
			currentRaid.calculationVersion(), currentRaid.dataState(), result.lastSyncedAt());
		return new DemoTimelineDtos.View(FIXTURE_ID, INITIAL_GOAL_AMOUNT_KRW, TARGET_GOAL_AMOUNT_KRW,
			frameIndex, frames(), goal, raid, "demo-timeline-v2", "FRESH", result.lastSyncedAt());
	}

	private List<DemoTimelineDtos.Frame> frames() {
		return java.util.stream.IntStream.range(0, FRAME_COUNT)
			.mapToObj(DemoStageSnapshot::forFrame)
			.map(snapshot -> new DemoTimelineDtos.Frame(snapshot.frameIndex(), snapshot.month(),
				snapshot.savingEventKrw(), snapshot.amountKrw(),
				GoalProgress.normalizedBps(INITIAL_GOAL_AMOUNT_KRW, TARGET_GOAL_AMOUNT_KRW, snapshot.amountKrw()),
				"FRESH"))
			.toList();
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
