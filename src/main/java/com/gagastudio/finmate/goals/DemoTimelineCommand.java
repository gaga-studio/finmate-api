package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_demo_timeline_command")
class DemoTimelineCommand {
	@Id private UUID id;
	@Column(name = "user_id", nullable = false) private UUID userId;
	@Column(name = "fixture_id", nullable = false) private String fixtureId;
	@Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
	@Column(name = "request_expected_stage", nullable = false) private int requestExpectedFrameIndex;
	@Column(name = "stage", nullable = false) private int frameIndex;
	@Column(name = "goal_amount_krw", nullable = false) private long goalAmountKrw;
	@Column(name = "raid_progress_bps", nullable = false) private int raidProgressBps;
	@Column(name = "raid_stage", nullable = false) private int raidStage;
	@Column(name = "boss_hp_bps", nullable = false) private int bossHpBps;
	@Column(name = "spending_bps", nullable = false) private int spendingBps;
	@Column(name = "saving_bps", nullable = false) private int savingBps;
	@Column(name = "investment_judgment_bps", nullable = false) private int investmentJudgmentBps;
	@Column(name = "coach_copy_key", nullable = false) private String coachCopyKey;
	@Column(name = "synced_at", nullable = false) private Instant syncedAt;
	@Column(name = "original_response", nullable = false) private String originalResponse;
	protected DemoTimelineCommand() {
	}
	DemoTimelineCommand(UUID userId, String fixtureId, String idempotencyKey, int requestExpectedFrameIndex, int frameIndex,
		SyntheticSnapshotResult result, DemoStageSnapshot snapshot, GoalDtos.RaidView raid, String originalResponse) {
		this.id = UUID.randomUUID(); this.userId = userId; this.fixtureId = fixtureId; this.idempotencyKey = idempotencyKey;
		this.requestExpectedFrameIndex = requestExpectedFrameIndex;
		this.frameIndex = frameIndex; this.goalAmountKrw = result.currentAmountKrw(); this.raidProgressBps = result.highestProgressBps();
		this.raidStage = result.stage(); this.bossHpBps = result.bossHpBps(); this.spendingBps = snapshot.spendingBps();
		this.savingBps = snapshot.savingBps(); this.investmentJudgmentBps = snapshot.investmentJudgmentBps(); this.syncedAt = result.lastSyncedAt();
		this.coachCopyKey = raid.coachCopyKey();
		this.originalResponse = originalResponse;
	}
	int getFrameIndex() { return frameIndex; }
	UUID getUserId() { return userId; }
	int getRequestExpectedFrameIndex() { return requestExpectedFrameIndex; }
	long getGoalAmountKrw() { return goalAmountKrw; }
	int getRaidProgressBps() { return raidProgressBps; }
	int getRaidStage() { return raidStage; }
	int getBossHpBps() { return bossHpBps; }
	int getSpendingBps() { return spendingBps; }
	int getSavingBps() { return savingBps; }
	int getInvestmentJudgmentBps() { return investmentJudgmentBps; }
	String getCoachCopyKey() { return coachCopyKey; }
	Instant getSyncedAt() { return syncedAt; }
	String getOriginalResponse() { return originalResponse; }
}
