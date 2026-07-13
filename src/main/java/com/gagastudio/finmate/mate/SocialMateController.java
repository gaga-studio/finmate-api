package com.gagastudio.finmate.mate;

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
	SocialMateDtos.FriendOverview overview() {
		return service.overview();
	}

	@GetMapping("/feed")
	SocialMateDtos.FeedPage feed() {
		return service.feed();
	}

	@GetMapping("/streaks")
	SocialMateDtos.StreakPage streaks() {
		return service.streaks();
	}
}
