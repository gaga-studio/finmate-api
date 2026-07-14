package com.gagastudio.finmate.goals;

import java.io.Serializable;
import jakarta.persistence.Embeddable;

@Embeddable
class SyntheticRuntimePersonaId implements Serializable {
	private String sourcePersonaId;
	private String releaseVersion;

	protected SyntheticRuntimePersonaId() {
	}

	String getSourcePersonaId() { return sourcePersonaId; }
}
