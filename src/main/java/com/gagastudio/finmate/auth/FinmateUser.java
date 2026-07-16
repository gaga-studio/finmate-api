package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_user")
public class FinmateUser {
	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "onboarding_status", nullable = false)
	private String onboardingStatus = "NOT_STARTED";

	@Column(name = "housing_type")
	private String housingType;
	@Column(name = "employment_type")
	private String employmentType;
	@Column(name = "income_regularity")
	private String incomeRegularity;
	@Column(name = "has_dependents")
	private Boolean hasDependents;
	@Column(name = "primary_concern")
	private String primaryConcern;
	@Column(name = "change_pace")
	private String changePace;
	@Column(name = "risk_tolerance")
	private String riskTolerance;
	@Column(name = "notification_preference")
	private String notificationPreference;
	@Column(name = "context_tags")
	private String contextTags;
	@Column(name = "profile_consent_version")
	private String profileConsentVersion;

	@Column(name = "raid_motion", nullable = false)
	private String raidMotion = "REDUCED";
	@Column(name = "push_enabled", nullable = false)
	private boolean pushEnabled;
	@Column(nullable = false)
	private String locale = "ko-KR";
	@Column(name = "time_zone", nullable = false)
	private String timeZone = "Asia/Seoul";

	@Column(name = "privacy_id", nullable = false)
	private UUID privacyId;
	@Column(name = "anonymous_card_opt_in", nullable = false)
	private boolean anonymousCardOptIn;
	@Column(name = "exposed_fields", nullable = false)
	private String exposedFields = "[]";

	@Column(name = "privacy_updated_at", nullable = false)
	private Instant privacyUpdatedAt;

	@Column(name = "privacy_consent_version", nullable = false)
	private String privacyConsentVersion = "privacy-v1.0";
	@Column(name = "privacy_version", nullable = false)
	private long privacyVersion = 1;
	@Column(name = "share_consent_state", nullable = false)
	private String shareConsentState = "OPTED_OUT";

	protected FinmateUser() {
	}

	public FinmateUser(String email, String displayName, String passwordHash) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.privacyId = UUID.randomUUID();
		this.privacyUpdatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public String getEmail() { return email; }
	public String getDisplayName() { return displayName; }
	public String getPasswordHash() { return passwordHash; }
	public String getOnboardingStatus() { return onboardingStatus; }
	public String getHousingType() { return housingType; }
	public String getEmploymentType() { return employmentType; }
	public String getIncomeRegularity() { return incomeRegularity; }
	public Boolean getHasDependents() { return hasDependents; }
	public String getPrimaryConcern() { return primaryConcern; }
	public String getChangePace() { return changePace; }
	public String getRiskTolerance() { return riskTolerance; }
	public String getNotificationPreference() { return notificationPreference; }
	public String getContextTags() { return contextTags; }
	public String getProfileConsentVersion() { return profileConsentVersion; }
	public String getRaidMotion() { return raidMotion; }
	public boolean isPushEnabled() { return pushEnabled; }
	public String getLocale() { return locale; }
	public String getTimeZone() { return timeZone; }
	public UUID getPrivacyId() { return privacyId; }
	public boolean isAnonymousCardOptIn() { return anonymousCardOptIn; }
	public String getExposedFields() { return exposedFields; }
	public String getPrivacyConsentVersion() { return privacyConsentVersion; }
	public long getPrivacyVersion() { return privacyVersion; }
	public Instant getPrivacyUpdatedAt() { return privacyUpdatedAt; }
	public String getShareConsentState() { return shareConsentState; }

	public void activateDisclosure(String serializedFields, String consentVersion) {
		this.anonymousCardOptIn = true;
		this.exposedFields = serializedFields;
		this.privacyConsentVersion = consentVersion;
		this.shareConsentState = "ACTIVE";
		this.privacyVersion += 1;
		this.privacyUpdatedAt = Instant.now();
	}

	public void withdrawDisclosure() {
		this.anonymousCardOptIn = false;
		this.exposedFields = "[]";
		this.shareConsentState = "OPTED_OUT";
		this.privacyVersion += 1;
		this.privacyUpdatedAt = Instant.now();
	}

	public void saveOnboarding(MeDtos.OnboardingProfile profile, String serializedContextTags) {
		this.housingType = profile.housingType();
		this.employmentType = profile.employmentType();
		this.incomeRegularity = profile.incomeRegularity();
		this.hasDependents = profile.hasDependents();
		this.primaryConcern = profile.primaryConcern();
		this.changePace = profile.changePace();
		this.riskTolerance = profile.riskTolerance();
		this.notificationPreference = profile.notificationPreference();
		this.contextTags = serializedContextTags;
		this.profileConsentVersion = profile.profileConsentVersion();
		this.onboardingStatus = "COMPLETED";
	}

	void completeGoalOnboarding(String displayName, boolean anonymousShareConsent) {
		this.displayName = displayName;
		this.onboardingStatus = "COMPLETED";
		if (anonymousShareConsent && !anonymousCardOptIn) {
			this.anonymousCardOptIn = true;
			this.shareConsentState = "ACTIVE";
			this.privacyVersion += 1;
			this.privacyUpdatedAt = Instant.now();
		}
	}

	public void savePreferences(MeDtos.UserPreferences preferences) {
		this.raidMotion = preferences.raidMotion();
		this.pushEnabled = preferences.pushEnabled();
		this.locale = preferences.locale();
		this.timeZone = preferences.timeZone();
	}
}
