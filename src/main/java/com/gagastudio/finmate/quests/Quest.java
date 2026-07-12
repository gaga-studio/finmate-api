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
	@Column(name = "created_at", nullable = false) private Instant createdAt;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;

	protected Quest() {
	}
	Quest(UUID userId, String templateCode, int displayOrder, String title, String verificationKind, int xpReward, Instant now) {
		this.id = UUID.randomUUID(); this.userId = userId; this.templateCode = templateCode; this.displayOrder = displayOrder;
		this.title = title; this.status = "AVAILABLE"; this.verificationKind = verificationKind; this.xpReward = xpReward;
		this.createdAt = now; this.updatedAt = now;
	}
	UUID getId() { return id; }
	int getDisplayOrder() { return displayOrder; }
	String getTitle() { return title; }
	String getStatus() { return status; }
	String getVerificationKind() { return verificationKind; }
	int getXpReward() { return xpReward; }
	void markDataPending(Instant now) { status = "DATA_PENDING"; updatedAt = now; }
	void markCompleted(Instant now) { status = "COMPLETED"; updatedAt = now; }
}
