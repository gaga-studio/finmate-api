package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class MeDtos {
	private MeDtos() {
	}

	record OnboardingProfile(
		@NotBlank @Pattern(regexp = "WITH_FAMILY|MONTHLY_RENT|JEONSE|OWN|OTHER") String housingType,
		@NotBlank @Pattern(regexp = "EMPLOYEE|SELF_EMPLOYED|PART_TIME|STUDENT|UNEMPLOYED|OTHER") String employmentType,
		@NotBlank @Pattern(regexp = "REGULAR|IRREGULAR|NONE") String incomeRegularity,
		@NotNull Boolean hasDependents,
		@NotBlank @Pattern(regexp = "SAVING|SPENDING") String primaryConcern,
		@NotBlank @Pattern(regexp = "GENTLE|BALANCED|AMBITIOUS") String changePace,
		@NotBlank @Pattern(regexp = "CONSERVATIVE|MODERATE|AGGRESSIVE|NOT_ASSESSED") String riskTolerance,
		@NotBlank @Pattern(regexp = "IMPORTANT_ONLY|DAILY_SUMMARY|NONE") String notificationPreference,
		@NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 30) String> contextTags,
		@NotBlank @Pattern(regexp = "profile-consent-v[0-9]+\\.[0-9]+") String profileConsentVersion) {
	}

	record UserPreferences(
		@NotBlank @Pattern(regexp = "FULL|REDUCED|OFF") String raidMotion,
		@NotNull Boolean pushEnabled,
		@NotBlank @Pattern(regexp = "ko-KR") String locale,
		@NotBlank @Pattern(regexp = "Asia/Seoul") String timeZone) {
	}

	record PrivacySettings(UUID consentAggregateId, boolean anonymousCardOptIn, List<String> exposedFields,
		String consentVersion, long version, Instant updatedAt, String shareConsentState) {
	}

	record MeResponse(AuthDtos.UserSummary user, OnboardingProfile onboarding,
		UserPreferences preferences, PrivacySettings privacy) {
	}
}
