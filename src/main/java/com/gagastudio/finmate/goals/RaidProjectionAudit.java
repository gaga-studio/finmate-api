package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_raid_projection_audit")
class RaidProjectionAudit {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "goal_id", nullable = false)
	private UUID goalId;
	@Column(name = "raid_id", nullable = false)
	private UUID raidId;
	@Column(name = "current_progress_bps", nullable = false)
	private int currentProgressBps;
	@Column(name = "highest_progress_bps", nullable = false)
	private int highestProgressBps;
	@Column(nullable = false)
	private int stage;
	@Column(name = "boss_hp_bps", nullable = false)
	private int bossHpBps;
	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	protected RaidProjectionAudit() {
	}

	RaidProjectionAudit(RaidProjection raid, Instant recordedAt) {
		this.id = UUID.randomUUID();
		this.userId = raid.getUserId();
		this.goalId = raid.getGoalId();
		this.raidId = raid.getId();
		this.currentProgressBps = raid.getCurrentProgressBps();
		this.highestProgressBps = raid.getHighestProgressBps();
		this.stage = raid.getStage();
		this.bossHpBps = raid.getBossHpBps();
		this.recordedAt = recordedAt;
	}
}
