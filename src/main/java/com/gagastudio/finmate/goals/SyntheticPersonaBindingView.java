package com.gagastudio.finmate.goals;

record SyntheticPersonaBindingView(String status, String sourcePersonaId, String releaseVersion) {
	static SyntheticPersonaBindingView insufficient() {
		return new SyntheticPersonaBindingView("INSUFFICIENT", null, null);
	}
}
