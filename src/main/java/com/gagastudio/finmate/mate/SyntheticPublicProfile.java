package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_synthetic_public_profile")
class SyntheticPublicProfile {
	@Id
	private UUID id;
	@Column(name = "source_persona_id", nullable = false, unique = true)
	private String sourcePersonaId;
	@Column(name = "owner_user_id", unique = true)
	private UUID ownerUserId;
	@Column(nullable = false)
	private String alias;
	@Column(name = "visible_fields", nullable = false)
	private String visibleFields;
	@Column(name = "exact_values", nullable = false)
	private boolean exactValues;
	@Column(nullable = false)
	private boolean synthetic;
	@Column(name = "consent_state", nullable = false)
	private String consentState;
	@Column(name = "consent_version", nullable = false)
	private String consentVersion;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SyntheticPublicProfile() {
	}

	UUID getId() { return id; }
	String getAlias() { return alias; }
	String getVisibleFields() { return visibleFields; }
	boolean isExactValues() { return exactValues; }
	boolean isSynthetic() { return synthetic; }
	String getConsentState() { return consentState; }
	String getConsentVersion() { return consentVersion; }
	Instant getUpdatedAt() { return updatedAt; }

	void activateDisclosure(String visibleFields, String consentVersion, Instant updatedAt) {
		this.visibleFields = visibleFields;
		this.exactValues = true;
		this.consentState = "ACTIVE";
		this.consentVersion = consentVersion;
		this.updatedAt = updatedAt;
	}

	void withdrawDisclosure(Instant updatedAt) {
		this.visibleFields = "[]";
		this.exactValues = false;
		this.consentState = "OPTED_OUT";
		this.updatedAt = updatedAt;
	}
}
