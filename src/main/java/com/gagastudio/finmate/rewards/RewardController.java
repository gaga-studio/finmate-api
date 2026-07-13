package com.gagastudio.finmate.rewards;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rewards")
class RewardController {
	private final PointService points;

	RewardController(PointService points) {
		this.points = points;
	}

	@GetMapping("/points")
	RewardDtos.PointLedgerView points(@AuthenticationPrincipal Jwt jwt) {
		return points.ledger(userId(jwt));
	}

	@GetMapping("/cosmetics")
	RewardDtos.CosmeticCatalogView cosmetics(@AuthenticationPrincipal Jwt jwt) {
		return points.catalog(userId(jwt));
	}

	@PostMapping("/cosmetics/{cosmeticId}/purchase")
	RewardDtos.CosmeticPurchaseView purchase(@AuthenticationPrincipal Jwt jwt, @PathVariable String cosmeticId,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return points.purchase(userId(jwt), cosmeticId, idempotencyKey);
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
