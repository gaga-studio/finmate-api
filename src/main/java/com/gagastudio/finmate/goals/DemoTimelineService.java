package com.gagastudio.finmate.goals;

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
	DemoTimelineService(DemoFixtureStateRepository states, DemoTimelineCommandRepository commands,
		SyntheticSnapshotIngestionService ingestion, GoalService goals, QuestService quests, RecordService records) {
		this.states = states; this.commands = commands; this.ingestion = ingestion; this.goals = goals; this.quests = quests; this.records = records;
	}

	@Transactional
	DemoTimelineDtos.View advance(UUID userId, String fixtureId, int expectedStage, String idempotencyKey) {
		if (!FIXTURE_ID.equals(fixtureId)) throw new InvalidDemoTimelineException("Unsupported demo fixture");
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidDemoTimelineException("Idempotency-Key must be 16 to 128 characters");
		}
		DemoTimelineCommand replay = commands.findByUserIdAndFixtureIdAndIdempotencyKey(userId, fixtureId, idempotencyKey).orElse(null);
		if (replay != null) return view(replay);
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
		DemoTimelineCommand command = commands.save(new DemoTimelineCommand(userId, fixtureId, idempotencyKey, nextStage, result,
			snapshot, goals.currentRaid(userId)));
		return view(command);
	}

	private DemoTimelineDtos.View view(DemoTimelineCommand command) {
		GoalDtos.UserGoalView currentGoal = goals.activeGoal(command.getUserId());
		GoalDtos.RaidView currentRaid = goals.currentRaid(command.getUserId());
		GoalDtos.UserGoalView goal = new GoalDtos.UserGoalView(currentGoal.goalId(), currentGoal.title(), currentGoal.domain(),
			command.getGoalAmountKrw(), currentGoal.targetAmountKrw(), currentGoal.targetMonth(), currentGoal.state(),
			currentGoal.confirmedAt(), currentGoal.calculationVersion(), currentGoal.dataState(), command.getSyncedAt());
		GoalDtos.RaidView raid = new GoalDtos.RaidView(currentRaid.raidId(), currentRaid.goalId(), command.getRaidStage(),
			command.getBossHpBps(), command.getRaidProgressBps(), new GoalDtos.FinancialStatsView(command.getSpendingBps(),
			command.getSavingBps(), command.getInvestmentJudgmentBps()), currentRaid.xp(), command.getCoachCopyKey(),
			currentRaid.calculationVersion(), currentRaid.dataState(), command.getSyncedAt());
		return new DemoTimelineDtos.View(FIXTURE_ID, command.getStage(), goal, raid,
			new DemoTimelineDtos.SyntheticGroupView("group-demo-10", "Demo adventurers", 10, true, false));
	}
}
