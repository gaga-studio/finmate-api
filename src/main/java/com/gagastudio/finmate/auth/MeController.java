package com.gagastudio.finmate.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me")
class MeController {
	private final AuthService authService;
	private final FinmateUserRepository users;
	private final ObjectMapper objectMapper;

	MeController(AuthService authService, FinmateUserRepository users, ObjectMapper objectMapper) {
		this.authService = authService;
		this.users = users;
		this.objectMapper = objectMapper;
	}

	@GetMapping
	MeDtos.MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return response(authService.user(jwt.getSubject()));
	}

	@PutMapping("/onboarding")
	@Transactional
	MeDtos.MeResponse saveOnboarding(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MeDtos.OnboardingProfile profile)
		throws JsonProcessingException {
		FinmateUser user = authService.user(jwt.getSubject());
		user.saveOnboarding(profile, objectMapper.writeValueAsString(profile.contextTags()));
		return response(users.save(user));
	}

	@PutMapping("/preferences")
	@Transactional
	MeDtos.UserPreferences savePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MeDtos.UserPreferences preferences) {
		FinmateUser user = authService.user(jwt.getSubject());
		user.savePreferences(preferences);
		users.save(user);
		return preferences(user);
	}

	private MeDtos.MeResponse response(FinmateUser user) {
		return new MeDtos.MeResponse(summary(user), onboarding(user), preferences(user), privacy(user));
	}

	private AuthDtos.UserSummary summary(FinmateUser user) {
		return new AuthDtos.UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getOnboardingStatus());
	}

	private MeDtos.OnboardingProfile onboarding(FinmateUser user) {
		if (user.getHousingType() == null) return null;
		try {
			List<String> tags = objectMapper.readValue(user.getContextTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
			return new MeDtos.OnboardingProfile(user.getHousingType(), user.getEmploymentType(), user.getIncomeRegularity(),
				user.getHasDependents(), user.getPrimaryConcern(), user.getChangePace(), user.getRiskTolerance(),
				user.getNotificationPreference(), tags, user.getProfileConsentVersion());
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored onboarding tags are invalid", exception);
		}
	}

	private MeDtos.UserPreferences preferences(FinmateUser user) {
		return new MeDtos.UserPreferences(user.getRaidMotion(), user.isPushEnabled(), user.getLocale(), user.getTimeZone());
	}

	private MeDtos.PrivacySettings privacy(FinmateUser user) {
		return new MeDtos.PrivacySettings(user.getPrivacyId(), false, List.of(), user.getPrivacyConsentVersion(),
			user.getPrivacyVersion(), user.getPrivacyUpdatedAt(), user.getShareConsentState());
	}
}
