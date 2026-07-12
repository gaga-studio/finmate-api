package com.gagastudio.finmate.goals;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyntheticSnapshotIngestionService {
	private final UserGoalRepository goals;
	private final SyntheticFinancialSnapshotRepository snapshots;
	private final RaidProjectionRepository raids;
	private final RaidProjectionAuditRepository raidAudits;
	private final RaidProgressProjector projector;

	SyntheticSnapshotIngestionService(UserGoalRepository goals, SyntheticFinancialSnapshotRepository snapshots,
		RaidProjectionRepository raids, RaidProjectionAuditRepository raidAudits, RaidProgressProjector projector) {
		this.goals = goals;
		this.snapshots = snapshots;
		this.raids = raids;
		this.raidAudits = raidAudits;
		this.projector = projector;
	}

	@Transactional
	public SyntheticSnapshotResult ingest(UUID userId, SyntheticSnapshotInput input) {
		UserGoal goal = goals.findActiveForUpdate(userId).orElseThrow(MainGoalNotFoundException::new);
		RaidProjection raid = raids.findForUpdate(userId, goal.getId())
			.orElseGet(() -> new RaidProjection(userId, goal, input.lastSyncedAt()));
		int currentProgressBps = projector.progressBps(raid.getConfirmedBaselineAmountKrw(), goal.getTargetAmountKrw(),
			new FinancialSnapshotData(input.observedGoalAmountKrw(), input.xp()));

		snapshots.save(new SyntheticFinancialSnapshot(userId, goal, input));
		goal.applySnapshot(input.observedGoalAmountKrw(), input.lastSyncedAt());
		raid.applyProgress(currentProgressBps, input.lastSyncedAt());
		raids.save(raid);
		raidAudits.save(new RaidProjectionAudit(raid, input.lastSyncedAt()));

		return new SyntheticSnapshotResult(goal.getId(), raid.getId(), goal.getCurrentAmountKrw(),
			raid.getCurrentProgressBps(), raid.getHighestProgressBps(), raid.getStage(), raid.getBossHpBps(),
			input.lastSyncedAt());
	}
}
