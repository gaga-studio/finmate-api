package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_user_goal")
class UserGoal {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(nullable = false)
	private String title;
	@Column(nullable = false)
	private String domain;
	@Column(name = "current_amount_krw", nullable = false)
	private long currentAmountKrw;
	@Column(name = "target_amount_krw", nullable = false)
	private long targetAmountKrw;
	@Column(name = "target_month", nullable = false)
	private LocalDate targetMonth;
	@Column(nullable = false)
	private String state;
	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;
	@Column(name = "calculation_version", nullable = false)
	private String calculationVersion;
	@Column(name = "data_state", nullable = false)
	private String dataState;
	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;

	protected UserGoal() {
	}

	UserGoal(UUID userId, GoalDraft draft, Instant confirmedAt, Instant lastSyncedAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.title = draft.title();
		this.domain = draft.domain();
		this.currentAmountKrw = draft.currentAmountKrw();
		this.targetAmountKrw = draft.targetAmountKrw();
		this.targetMonth = draft.targetMonth().atDay(1);
		this.state = "ACTIVE";
		this.confirmedAt = confirmedAt;
		this.calculationVersion = "goal-calc-v1";
		this.dataState = "FRESH";
		this.lastSyncedAt = lastSyncedAt;
	}

	UUID getId() { return id; }
	UUID getUserId() { return userId; }
	String getTitle() { return title; }
	String getDomain() { return domain; }
	long getCurrentAmountKrw() { return currentAmountKrw; }
	long getTargetAmountKrw() { return targetAmountKrw; }
	LocalDate getTargetMonth() { return targetMonth; }
	String getState() { return state; }
	Instant getConfirmedAt() { return confirmedAt; }
	String getCalculationVersion() { return calculationVersion; }
	String getDataState() { return dataState; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
}
