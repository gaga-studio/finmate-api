package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_raid_projection")
class RaidProjection {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "goal_id", nullable = false)
	private UUID goalId;
	@Column(name = "confirmed_baseline_amount_krw", nullable = false)
	private long confirmedBaselineAmountKrw;
	@Column(name = "current_progress_bps", nullable = false)
	private int currentProgressBps;
	@Column(name = "highest_progress_bps", nullable = false)
	private int highestProgressBps;
	@Column(nullable = false)
	private int stage;
	@Column(name = "boss_hp_bps", nullable = false)
	private int bossHpBps;
	@Column(name = "coach_copy_key", nullable = false)
	private String coachCopyKey;
	@Column(name = "calculation_version", nullable = false)
	private String calculationVersion;
	@Column(name = "data_state", nullable = false)
	private String dataState;
	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;

	protected RaidProjection() {
	}

	RaidProjection(UUID userId, UserGoal goal, Instant lastSyncedAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.goalId = goal.getId();
		this.confirmedBaselineAmountKrw = goal.getCurrentAmountKrw();
		this.currentProgressBps = 0;
		this.highestProgressBps = 0;
		this.stage = 1;
		this.bossHpBps = GoalProgress.bossHpBpsForHighestProgress(0);
		this.coachCopyKey = "RAID_STAGE_1_WAITING_V2";
		this.calculationVersion = "raid-calc-v2";
		this.dataState = "FRESH";
		this.lastSyncedAt = lastSyncedAt;
	}

	UUID getId() { return id; }
	UUID getUserId() { return userId; }
	UUID getGoalId() { return goalId; }
	long getConfirmedBaselineAmountKrw() { return confirmedBaselineAmountKrw; }
	int getCurrentProgressBps() { return currentProgressBps; }
	int getHighestProgressBps() { return highestProgressBps; }
	int getStage() { return stage; }
	int getBossHpBps() { return bossHpBps; }
	String getCoachCopyKey() { return coachCopyKey; }
	String getCalculationVersion() { return calculationVersion; }
	String getDataState() { return dataState; }
	Instant getLastSyncedAt() { return lastSyncedAt; }

	void applyProgress(int progressBps, Instant syncedAt) {
		this.currentProgressBps = progressBps;
		this.highestProgressBps = Math.max(highestProgressBps, progressBps);
		this.stage = GoalProgress.stageForHighestProgress(highestProgressBps);
		this.bossHpBps = GoalProgress.bossHpBpsForHighestProgress(highestProgressBps);
		this.coachCopyKey = highestProgressBps == 10_000
			? "RAID_COMPLETE_V2"
			: "RAID_STAGE_%d_WAITING_V2".formatted(stage);
		this.dataState = "FRESH";
		this.lastSyncedAt = syncedAt;
	}
}
