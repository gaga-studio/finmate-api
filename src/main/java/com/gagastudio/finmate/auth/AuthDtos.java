package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthDtos {
	private AuthDtos() {
	}

	record SignUpRequest(
		@Email @NotBlank @Size(max = 254) String email,
		@NotBlank String password,
		@NotBlank @Size(max = 30) String displayName) {
		@AssertTrue(message = "password must contain between 12 and 72 UTF-8 bytes")
		boolean isPasswordWithinPolicy() {
			return PasswordPolicy.isValid(password);
		}
	}

	record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
	}

	record UserSummary(UUID userId, String email, String displayName, String onboardingStatus) {
	}

	record AuthSession(String accessToken, String tokenType, Instant expiresAt, UserSummary user) {
	}
}
