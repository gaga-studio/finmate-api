package com.gagastudio.finmate.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
	private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
	private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

	private final FinmateUserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final JwtEncoder jwtEncoder;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	AuthService(FinmateUserRepository users, RefreshTokenRepository refreshTokens, JwtEncoder jwtEncoder) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.jwtEncoder = jwtEncoder;
	}

	@Transactional
	AuthenticatedSession signUp(AuthDtos.SignUpRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		if (users.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}
		FinmateUser user;
		try {
			user = users.saveAndFlush(new FinmateUser(email, request.displayName().trim(), passwordEncoder.encode(request.password())));
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
		return issueSession(user);
	}

	@Transactional
	AuthenticatedSession login(AuthDtos.LoginRequest request) {
		FinmateUser user = users.findByEmail(EmailNormalizer.normalize(request.email()))
			.orElseThrow(InvalidCredentialsException::new);
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return issueSession(user);
	}

	@Transactional
	AuthenticatedSession refresh(String rawToken) {
		RefreshToken token = activeToken(rawToken);
		token.revoke();
		return issueSession(token.getUser());
	}

	@Transactional
	void logout(String rawToken, String userId) {
		RefreshToken token = activeToken(rawToken);
		if (!token.getUser().getId().toString().equals(userId)) {
			throw new InvalidCredentialsException();
		}
		token.revoke();
	}

	FinmateUser user(String userId) {
		return users.findById(java.util.UUID.fromString(userId)).orElseThrow(InvalidCredentialsException::new);
	}

	private RefreshToken activeToken(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new InvalidCredentialsException();
		}
		return refreshTokens.findByTokenHashAndRevokedAtIsNull(RefreshTokenHasher.sha256(rawToken))
			.filter(token -> token.getExpiresAt().isAfter(Instant.now()))
			.orElseThrow(InvalidCredentialsException::new);
	}

	private AuthenticatedSession issueSession(FinmateUser user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);
		String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
			JwsHeader.with(MacAlgorithm.HS256).build(),
			JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(issuedAt).expiresAt(expiresAt)
				.claim("email", user.getEmail()).build())).getTokenValue();
		String refreshToken = RefreshTokenHasher.newToken();
		refreshTokens.save(new RefreshToken(user, RefreshTokenHasher.sha256(refreshToken), issuedAt.plus(REFRESH_TOKEN_LIFETIME)));
		AuthDtos.UserSummary summary = new AuthDtos.UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getOnboardingStatus());
		return new AuthenticatedSession(new AuthDtos.AuthSession(accessToken, "Bearer", expiresAt, summary), refreshToken);
	}

	record AuthenticatedSession(AuthDtos.AuthSession response, String refreshToken) {
	}
}
