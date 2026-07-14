package com.gagastudio.finmate.records;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_record_event")
class RecordEvent {
	@Id private UUID id;
	@Column(name = "user_id", nullable = false) private UUID userId;
	@Column(name = "record_date", nullable = false) private LocalDate recordDate;
	@Column(name = "occurred_at", nullable = false) private Instant occurredAt;
	@Column(name = "event_type", nullable = false) private String eventType;
	@Column(nullable = false) private String title;
	@Column(name = "xp_earned", nullable = false) private int xpEarned;
	@Column(name = "amount_krw") private Long amountKrw;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	protected RecordEvent() {
	}
	RecordEvent(UUID userId, Instant occurredAt, String eventType, String title, int xpEarned) {
		this(userId, occurredAt, eventType, title, xpEarned, null);
	}
	RecordEvent(UUID userId, Instant occurredAt, String eventType, String title, int xpEarned, Long amountKrw) {
		this.id = UUID.randomUUID(); this.userId = userId;
		this.recordDate = occurredAt.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate();
		this.occurredAt = occurredAt; this.eventType = eventType; this.title = title; this.xpEarned = xpEarned;
		this.amountKrw = amountKrw; this.createdAt = occurredAt;
	}
	UUID getId() { return id; }
	LocalDate getRecordDate() { return recordDate; }
	Instant getOccurredAt() { return occurredAt; }
	String getEventType() { return eventType; }
	String getTitle() { return title; }
	int getXpEarned() { return xpEarned; }
	Long getAmountKrw() { return amountKrw; }
}
