package com.gagastudio.finmate.rewards;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_point_ledger")
class PointLedgerEntry {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "entry_type", nullable = false)
	private String entryType;
	@Column(name = "amount_points", nullable = false)
	private int amountPoints;
	@Column(name = "source_type", nullable = false)
	private String sourceType;
	@Column(name = "source_id", nullable = false)
	private String sourceId;
	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected PointLedgerEntry() {
	}

	PointLedgerEntry(UUID userId, String entryType, int amountPoints, String sourceType, String sourceId,
		String idempotencyKey, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.entryType = entryType;
		this.amountPoints = amountPoints;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.idempotencyKey = idempotencyKey;
		this.occurredAt = occurredAt;
	}

	String getEntryType() { return entryType; }
	int getAmountPoints() { return amountPoints; }
	String getSourceType() { return sourceType; }
	String getSourceId() { return sourceId; }
	String getIdempotencyKey() { return idempotencyKey; }
	Instant getOccurredAt() { return occurredAt; }
}
