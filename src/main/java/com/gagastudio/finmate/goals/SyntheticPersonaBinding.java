package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_user_synthetic_persona_binding")
class SyntheticPersonaBinding {
	@Id
	@Column(name = "user_id")
	private UUID userId;
	@Column(name = "source_persona_id", nullable = false)
	private String sourcePersonaId;
	@Column(name = "release_version", nullable = false)
	private String releaseVersion;
	@Column(name = "bound_at", nullable = false)
	private Instant boundAt;

	protected SyntheticPersonaBinding() {
	}

	SyntheticPersonaBinding(UUID userId, String sourcePersonaId, String releaseVersion, Instant boundAt) {
		this.userId = userId;
		this.sourcePersonaId = sourcePersonaId;
		this.releaseVersion = releaseVersion;
		this.boundAt = boundAt;
	}

	String getSourcePersonaId() { return sourcePersonaId; }
	String getReleaseVersion() { return releaseVersion; }
}
