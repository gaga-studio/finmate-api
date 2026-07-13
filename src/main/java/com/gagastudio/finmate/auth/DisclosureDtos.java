package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

final class DisclosureDtos {
	private DisclosureDtos() {
	}

	record DisclosureRequest(
		@NotEmpty Set<DisclosureField> fields,
		@NotBlank @Pattern(regexp = "financial-disclosure-v[0-9]+\\.[0-9]+") String consentVersion,
		boolean confirmExactValues
	) {
		@AssertTrue(message = "Exact financial values must be explicitly confirmed")
		boolean isExactValuesConfirmed() {
			return confirmExactValues;
		}
	}

	record DisclosureSettings(String state, boolean exactValues, List<DisclosureField> fields,
		String consentVersion, long version, Instant updatedAt) {
	}

	record DisclosurePreview(boolean exactValues, List<DisclosureField> fields,
		List<String> permanentlyExcludedFields, String consentVersion) {
	}
}
