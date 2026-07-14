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
	@Column(name = "onboarding_state", nullable = false)
	private String onboardingState;
	@Column(name = "income_regularity", nullable = false)
	private String incomeRegularity;
	@Column(name = "housing_type", nullable = false)
	private String housingType;
	@Column(name = "fixed_cost_burden", nullable = false)
	private String fixedCostBurden;
	@Column(name = "money_concern", nullable = false)
	private String moneyConcern;
	@Column(name = "financial_tendency", nullable = false)
	private String financialTendency;
	@Column(name = "lifestyle_tags", nullable = false)
	private String lifestyleTags;
	@Column(name = "anonymous_share_consent", nullable = false)
	private boolean anonymousShareConsent;
	@Column(name = "synthetic_mydata_consent", nullable = false)
	private boolean syntheticMyDataConsent;
	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;

	protected OnboardingState() {
	}

	OnboardingState(UUID userId, String displayName, Instant completedAt, String idempotencyKey,
		GoalDtos.CompleteOnboardingRequest request) {
		this.userId = userId;
		this.displayName = displayName;
		this.status = "COMPLETED";
		this.completedAt = completedAt;
		this.idempotencyKey = idempotencyKey;
		GoalDtos.ProfileContext context = request.resolvedContext();
		this.onboardingState = request.isLegacy() ? "GOAL_ACTIVE" : "EXPLORE_ONLY";
		this.incomeRegularity = context.incomeRegularity();
		this.housingType = context.housingType();
		this.fixedCostBurden = context.fixedCostBurden();
		this.moneyConcern = request.resolvedMoneyConcern();
		this.financialTendency = request.resolvedFinancialTendency();
		this.lifestyleTags = String.join("|", request.resolvedLifestyleTags());
		this.anonymousShareConsent = Boolean.TRUE.equals(request.anonymousShareConsent());
		this.syntheticMyDataConsent = request.isLegacy() || Boolean.TRUE.equals(request.syntheticMyDataConsent());
		this.lastSyncedAt = completedAt;
	}

	String getDisplayName() { return displayName; }
	String getStatus() { return status; }
	String getOnboardingState() { return onboardingState; }
	GoalDtos.ProfileContext context() { return new GoalDtos.ProfileContext(incomeRegularity, housingType, fixedCostBurden); }
	java.util.List<String> lifestyleTags() {
		return lifestyleTags.isBlank() ? java.util.List.of() : java.util.List.of(lifestyleTags.split("\\|"));
	}
	String getMoneyConcern() { return moneyConcern; }
	String getFinancialTendency() { return financialTendency; }
	boolean isAnonymousShareConsent() { return anonymousShareConsent; }
	boolean isSyntheticMyDataConsent() { return syntheticMyDataConsent; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
	boolean hasIdempotencyKey(String key) { return idempotencyKey.equals(key); }
}
