package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

	SyntheticFinancialSnapshot(UUID userId, UserGoal goal, SyntheticSnapshotInput input) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.goalId = goal.getId();
		this.snapshotMonth = input.lastSyncedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().withDayOfMonth(1);
		this.observedGoalAmountKrw = input.observedGoalAmountKrw();
		this.spendingBps = input.spendingBps();
		this.savingBps = input.savingBps();
		this.investmentJudgmentBps = input.investmentJudgmentBps();
		this.xp = input.xp();
		this.lastSyncedAt = input.lastSyncedAt();
	}

	FinancialSnapshotData toData() { return new FinancialSnapshotData(observedGoalAmountKrw, xp); }
	LocalDate getSnapshotMonth() { return snapshotMonth; }
	int getSpendingBps() { return spendingBps; }
	int getSavingBps() { return savingBps; }
	int getInvestmentJudgmentBps() { return investmentJudgmentBps; }
	int getXp() { return xp; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
}
