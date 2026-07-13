package com.gagastudio.finmate.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.mate.PublicProfileDisclosureProjection;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/disclosures")
class DisclosureController {
	private static final List<String> PERMANENTLY_EXCLUDED_FIELDS = List.of(
		"ACCOUNT_NUMBER", "RAW_TRANSACTION_MEMO", "DETAILED_EMPLOYER", "DETAILED_LOCATION", "AUTHENTICATION_IDENTIFIER");

	private final AuthService authService;
	private final FinmateUserRepository users;
	private final ObjectMapper objectMapper;
	private final PublicProfileDisclosureProjection publicProfiles;

	DisclosureController(AuthService authService, FinmateUserRepository users, ObjectMapper objectMapper,
		PublicProfileDisclosureProjection publicProfiles) {
		this.authService = authService;
		this.users = users;
		this.objectMapper = objectMapper;
		this.publicProfiles = publicProfiles;
	}

	@GetMapping
	DisclosureDtos.DisclosureSettings settings(@AuthenticationPrincipal Jwt jwt) {
		return settings(authService.user(jwt.getSubject()));
	}

	@PostMapping("/preview")
	DisclosureDtos.DisclosurePreview preview(@Valid @RequestBody DisclosureDtos.DisclosureRequest request) {
		return new DisclosureDtos.DisclosurePreview(true, ordered(request.fields()), PERMANENTLY_EXCLUDED_FIELDS,
			request.consentVersion());
	}

	@PutMapping
	@Transactional
	DisclosureDtos.DisclosureSettings activate(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody DisclosureDtos.DisclosureRequest request) throws JsonProcessingException {
		FinmateUser user = authService.user(jwt.getSubject());
		List<DisclosureField> fields = ordered(request.fields());
		String serializedFields = objectMapper.writeValueAsString(fields);
		user.activateDisclosure(serializedFields, request.consentVersion());
		FinmateUser saved = users.save(user);
		publicProfiles.activate(saved.getId(), serializedFields, request.consentVersion(), saved.getPrivacyUpdatedAt());
		return settings(saved);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void withdraw(@AuthenticationPrincipal Jwt jwt) {
		FinmateUser user = authService.user(jwt.getSubject());
		user.withdrawDisclosure();
		FinmateUser saved = users.save(user);
		publicProfiles.withdraw(saved.getId(), saved.getPrivacyUpdatedAt());
	}

	private DisclosureDtos.DisclosureSettings settings(FinmateUser user) {
		return new DisclosureDtos.DisclosureSettings(user.getShareConsentState(), user.isAnonymousCardOptIn(),
			readFields(user), user.getPrivacyConsentVersion(), user.getPrivacyVersion(), user.getPrivacyUpdatedAt());
	}

	private List<DisclosureField> readFields(FinmateUser user) {
		try {
			DisclosureField[] fields = objectMapper.readValue(user.getExposedFields(), DisclosureField[].class);
			return ordered(List.of(fields));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored disclosure fields are invalid", exception);
		}
	}

	private List<DisclosureField> ordered(Iterable<DisclosureField> fields) {
		java.util.ArrayList<DisclosureField> ordered = new java.util.ArrayList<>();
		fields.forEach(ordered::add);
		ordered.sort(Comparator.comparingInt(Enum::ordinal));
		return List.copyOf(ordered);
	}
}
