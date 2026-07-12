package com.gagastudio.finmate.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
class AuthController {
	private final AuthService authService;
	private final RefreshCookieFactory refreshCookieFactory;

	AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
		this.authService = authService;
		this.refreshCookieFactory = refreshCookieFactory;
	}

	@PostMapping("/signup")
	ResponseEntity<AuthDtos.AuthSession> signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
		AuthService.AuthenticatedSession session = authService.signUp(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.refreshCookie(session.refreshToken()).toString())
			.body(session.response());
	}

	@PostMapping("/login")
	ResponseEntity<AuthDtos.AuthSession> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
		AuthService.AuthenticatedSession session = authService.login(request);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookieFactory.refreshCookie(session.refreshToken()).toString()).body(session.response());
	}

	@PostMapping("/refresh")
	ResponseEntity<AuthDtos.AuthSession> refresh(@org.springframework.web.bind.annotation.CookieValue(value = "finmate_refresh", required = false) String token) {
		AuthService.AuthenticatedSession session = authService.refresh(token);
		return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
			.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.refreshCookie(session.refreshToken()).toString()).body(session.response());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,
		@org.springframework.web.bind.annotation.CookieValue(value = "finmate_refresh", required = false) String token) {
		authService.logout(token, jwt.getSubject());
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clearCookie().toString()).build();
	}
}
