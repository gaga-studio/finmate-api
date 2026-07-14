package com.gagastudio.finmate.quests;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_quest")
class Quest {
	@Id private UUID id;
	@Column(name = "user_id", nullable = false) private UUID userId;
	@Column(name = "template_code", nullable = false) private String templateCode;
	@Column(name = "display_order", nullable = false) private int displayOrder;
	@Column(nullable = false) private String title;
	@Column(nullable = false) private String status;
	@Column(name = "verification_kind", nullable = false) private String verificationKind;
	@Column(name = "xp_reward", nullable = false) private int xpReward;
	@Column(name = "point_reward", nullable = false) private int pointReward;
	@Column(name = "current_value", nullable = false) private int currentValue;
	@Column(name = "target_value", nullable = false) private int targetValue;
	@Column(nullable = false) private String unit;
	@Column(name = "duration_label", nullable = false) private String durationLabel;
	@Column(name = "accepted_at") private Instant acceptedAt;
	@Column(name = "accept_idempotency_key") private String acceptIdempotencyKey;
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;

	protected Quest() {
	}
	Quest(UUID userId, String templateCode, int displayOrder, String title, String verificationKind,
		int xpReward, int pointReward, Instant now) {
		this.id = UUID.randomUUID(); this.userId = userId; this.templateCode = templateCode; this.displayOrder = displayOrder;
		this.title = title; this.status = "AVAILABLE"; this.verificationKind = verificationKind; this.xpReward = xpReward;
		this.pointReward = pointReward; this.currentValue = 0; this.targetValue = 1; this.unit = "COUNT";
		this.durationLabel = "오늘까지";
		this.createdAt = now; this.updatedAt = now;
	}
	UUID getId() { return id; }
	String getTemplateCode() { return templateCode; }
	int getDisplayOrder() { return displayOrder; }
	String getTitle() { return title; }
	String getStatus() { return status; }
	String getVerificationKind() { return verificationKind; }
	int getXpReward() { return xpReward; }
	int getPointReward() { return pointReward; }
	int getCurrentValue() { return currentValue; }
	int getTargetValue() { return targetValue; }
	String getUnit() { return unit; }
	String getDurationLabel() { return durationLabel; }
	Instant getAcceptedAt() { return acceptedAt; }
	void accept(String idempotencyKey, Instant now) {
		status = "ACTIVE"; acceptedAt = now; acceptIdempotencyKey = idempotencyKey; updatedAt = now;
	}
	void markDataPending(Instant now) { status = "DATA_PENDING"; updatedAt = now; }
	void markCompleted(Instant now) { status = "COMPLETED"; currentValue = targetValue; updatedAt = now; }
}
