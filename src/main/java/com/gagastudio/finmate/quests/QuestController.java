package com.gagastudio.finmate.quests;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quests")
class QuestController {
	private final QuestService service;
	QuestController(QuestService service) { this.service = service; }
	@GetMapping
	QuestDtos.QuestPage list(@AuthenticationPrincipal Jwt jwt) { return service.list(UUID.fromString(jwt.getSubject())); }
	@GetMapping("/{questId}")
	QuestDtos.QuestView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questId) {
		return service.get(UUID.fromString(jwt.getSubject()), questId);
	}
	@PostMapping("/{questId}/accept")
	QuestDtos.QuestAcceptanceView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questId,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return service.accept(UUID.fromString(jwt.getSubject()), questId, idempotencyKey);
	}
	@PostMapping("/{questId}/complete")
	ResponseEntity<QuestDtos.QuestCompletionView> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questId,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		QuestService.CompletionResult result = service.complete(UUID.fromString(jwt.getSubject()), questId, idempotencyKey);
		return ResponseEntity.status(result.pending() ? 202 : 200).body(result.body());
	}
}
