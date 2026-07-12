package com.gagastudio.finmate.goals;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("demo")
@RestController
@RequestMapping("/api/v1/demo/timeline")
class DemoTimelineController {
	private final DemoTimelineService service;
	DemoTimelineController(DemoTimelineService service) { this.service = service; }
	@PostMapping("/advance")
	JsonNode advance(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody DemoTimelineDtos.AdvanceRequest request) {
		return service.advance(UUID.fromString(jwt.getSubject()), request.fixtureId(), request.expectedStage(), idempotencyKey);
	}
}
