package com.gagastudio.finmate.quests;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_quest_completion")
class QuestCompletion {
	@Id private UUID id;
	@Column(name = "user_id", nullable = false) private UUID userId;
	@Column(name = "quest_id", nullable = false) private UUID questId;
	@Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
	@Column(name = "xp_awarded", nullable = false) private int xpAwarded;
	@Column(name = "completed_at", nullable = false) private Instant completedAt;
	protected QuestCompletion() {
	}
	QuestCompletion(UUID userId, UUID questId, String idempotencyKey, int xpAwarded, Instant completedAt) {
		this.id = UUID.randomUUID(); this.userId = userId; this.questId = questId; this.idempotencyKey = idempotencyKey;
		this.xpAwarded = xpAwarded; this.completedAt = completedAt;
	}
	UUID getQuestId() { return questId; }
	int getXpAwarded() { return xpAwarded; }
	void award(int xpAwarded) { this.xpAwarded = xpAwarded; }
}
