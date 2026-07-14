package com.gagastudio.finmate.goals;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Embeddable;

@Embeddable
class SyntheticRuntimePersonaId implements Serializable {
	private String sourcePersonaId;
	private String releaseVersion;

	protected SyntheticRuntimePersonaId() {
	}

	String getSourcePersonaId() { return sourcePersonaId; }

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof SyntheticRuntimePersonaId that)) return false;
		return Objects.equals(sourcePersonaId, that.sourcePersonaId)
			&& Objects.equals(releaseVersion, that.releaseVersion);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sourcePersonaId, releaseVersion);
	}
}
