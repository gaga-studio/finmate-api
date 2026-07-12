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
	@Column(name = "goal_id", nullable = false, unique = true)
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

	RaidProjection(UserGoal goal, int progressBps, Instant lastSyncedAt) {
		this.id = UUID.randomUUID();
		this.goalId = goal.getId();
		this.confirmedBaselineAmountKrw = goal.getCurrentAmountKrw();
		this.currentProgressBps = progressBps;
		this.highestProgressBps = progressBps;
		this.stage = GoalProgress.stageForHighestProgress(progressBps);
		this.bossHpBps = 10_000;
		this.coachCopyKey = "RAID_STAGE_1_READY_V1";
		this.calculationVersion = "raid-calc-v1";
		this.dataState = "FRESH";
		this.lastSyncedAt = lastSyncedAt;
	}

	UUID getId() { return id; }
	UUID getGoalId() { return goalId; }
	int getCurrentProgressBps() { return currentProgressBps; }
	int getHighestProgressBps() { return highestProgressBps; }
	int getStage() { return stage; }
	int getBossHpBps() { return bossHpBps; }
	String getCoachCopyKey() { return coachCopyKey; }
	String getCalculationVersion() { return calculationVersion; }
	String getDataState() { return dataState; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
}
