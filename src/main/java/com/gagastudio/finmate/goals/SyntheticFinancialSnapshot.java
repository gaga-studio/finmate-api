package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_synthetic_financial_snapshot")
class SyntheticFinancialSnapshot {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "goal_id", nullable = false)
	private UUID goalId;
	@Column(name = "snapshot_month", nullable = false)
	private LocalDate snapshotMonth;
	@Column(name = "observed_goal_amount_krw", nullable = false)
	private long observedGoalAmountKrw;
	@Column(name = "spending_bps", nullable = false)
	private int spendingBps;
	@Column(name = "saving_bps", nullable = false)
	private int savingBps;
	@Column(name = "investment_judgment_bps", nullable = false)
	private int investmentJudgmentBps;
	@Column(nullable = false)
	private int xp;
	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;

	protected SyntheticFinancialSnapshot() {
	}

	SyntheticFinancialSnapshot(UUID userId, UserGoal goal, Instant lastSyncedAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.goalId = goal.getId();
		this.snapshotMonth = LocalDate.now().withDayOfMonth(1);
		this.observedGoalAmountKrw = goal.getCurrentAmountKrw();
		this.spendingBps = 5_200;
		this.savingBps = 1_800;
		this.investmentJudgmentBps = 4_000;
		this.xp = 0;
		this.lastSyncedAt = lastSyncedAt;
	}

	FinancialSnapshotData toData() { return new FinancialSnapshotData(observedGoalAmountKrw, xp); }
	LocalDate getSnapshotMonth() { return snapshotMonth; }
	int getSpendingBps() { return spendingBps; }
	int getSavingBps() { return savingBps; }
	int getInvestmentJudgmentBps() { return investmentJudgmentBps; }
	int getXp() { return xp; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
}
