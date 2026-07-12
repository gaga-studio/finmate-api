package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_onboarding_state")
class OnboardingState {
	@Id
	@Column(name = "user_id")
	private UUID userId;
	@Column(name = "display_name", nullable = false)
	private String displayName;
	@Column(nullable = false)
	private String status;
	@Column(name = "completed_at", nullable = false)
	private Instant completedAt;
	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	protected OnboardingState() {
	}

	OnboardingState(UUID userId, String displayName, Instant completedAt, String idempotencyKey) {
		this.userId = userId;
		this.displayName = displayName;
		this.status = "COMPLETED";
		this.completedAt = completedAt;
		this.idempotencyKey = idempotencyKey;
	}

	String getDisplayName() { return displayName; }
	String getStatus() { return status; }
	boolean hasIdempotencyKey(String key) { return idempotencyKey.equals(key); }
}
