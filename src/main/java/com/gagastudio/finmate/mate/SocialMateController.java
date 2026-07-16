package com.gagastudio.finmate.mate;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mate/friends")
class SocialMateController {
	private final SocialMateService service;

	SocialMateController(SocialMateService service) {
		this.service = service;
	}

	@GetMapping("/overview")
	SocialMateDtos.FriendOverview overview(@AuthenticationPrincipal Jwt jwt) {
		return service.overview(UUID.fromString(jwt.getSubject()));
	}

	@GetMapping("/feed")
	SocialMateDtos.FeedPage feed(@AuthenticationPrincipal Jwt jwt) {
		return service.feed(UUID.fromString(jwt.getSubject()));
	}

	@GetMapping("/streaks")
	SocialMateDtos.StreakPage streaks(@AuthenticationPrincipal Jwt jwt) {
		return service.streaks(UUID.fromString(jwt.getSubject()));
	}
}
