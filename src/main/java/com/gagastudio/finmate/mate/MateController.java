package com.gagastudio.finmate.mate;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
class MateController {
	private final MateService service;
	private final PublicFinancialProfileService publicProfiles;

	MateController(MateService service, PublicFinancialProfileService publicProfiles) {
		this.service = service;
		this.publicProfiles = publicProfiles;
	}

	@GetMapping("/mate/groups")
	MateDtos.MateGroupPage groups() {
		return service.groups();
	}

	@GetMapping("/mate/groups/{groupId}/adventurers")
	MateDtos.AdventurerPage adventurers(@PathVariable String groupId) {
		return service.adventurers(groupId);
	}

	@GetMapping("/mate/groups/{groupId}/report")
	MateDtos.MateGroupReportView groupReport(@PathVariable String groupId) {
		return service.groupReport(groupId);
	}

	@GetMapping("/mate/groups/{groupId}/adventurers/{adventurerId}")
	MateDtos.AdventurerView adventurer(@PathVariable String groupId, @PathVariable String adventurerId) {
		return service.adventurer(groupId, adventurerId);
	}

	@GetMapping("/mate/groups/{groupId}/adventurers/{adventurerId}/report")
	MateDtos.AdventurerReportView adventurerReport(@AuthenticationPrincipal Jwt jwt, @PathVariable String groupId,
		@PathVariable String adventurerId) {
		return service.adventurerReport(userId(jwt), groupId, adventurerId);
	}

	@PostMapping("/mate/explore/search")
	MateDtos.AdventurerPage search(@Valid @RequestBody MateDtos.MateExploreSearchRequest request) {
		return service.search(request);
	}

	@GetMapping("/mate/groups/{groupId}/adventurers/{adventurerId}/routines/{routineId}")
	MateDtos.RoutineView routine(@PathVariable String groupId, @PathVariable String adventurerId, @PathVariable String routineId) {
		return service.routine(groupId, adventurerId, routineId);
	}

	@GetMapping("/mate/groups/{groupId}/adventurers/{adventurerId}/financial-profile")
	PublicFinancialProfileDtos.PublicFinancialProfile publicFinancialProfile(@PathVariable String groupId,
		@PathVariable String adventurerId) {
		return publicProfiles.profile(groupId, adventurerId);
	}

	@PostMapping("/routine-adaptations")
	ResponseEntity<?> createAdaptation(@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody MateDtos.CreateAdaptationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.createRecommendation(userId(jwt), request));
	}

	@PutMapping("/routine-adaptations/{adaptationId}/choice")
	MateDtos.AdaptationSetView chooseDomain(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID adaptationId,
		@Valid @RequestBody MateDtos.ChooseDomainRequest request) {
		return service.chooseDomain(userId(jwt), adaptationId, request);
	}

	@PostMapping("/routine-adaptations/{adaptationId}/candidates/{candidateId}/import")
	ResponseEntity<MateDtos.ActiveBuildView> importCandidate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID adaptationId,
		@PathVariable String candidateId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		RoutineCommandResult<MateDtos.ActiveBuildView> result = service.importCandidate(userId(jwt), adaptationId, candidateId, idempotencyKey);
		return ResponseEntity.status(result.status()).body(result.body());
	}

	@GetMapping("/routine-builds/active")
	MateDtos.ActiveBuildView activeBuild(@AuthenticationPrincipal Jwt jwt) {
		return service.activeBuild(userId(jwt));
	}

	@PostMapping("/routine-builds/active/replacement")
	ResponseEntity<MateDtos.ReplacementView> replaceActiveBuild(@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody MateDtos.ReplaceBuildRequest request) {
		RoutineCommandResult<MateDtos.ReplacementView> result = service.replaceActiveBuild(userId(jwt), idempotencyKey, request);
		return ResponseEntity.status(result.status()).body(result.body());
	}

	@GetMapping("/hana-products/{productId}")
	MateDtos.RelatedHanaProductInfoView product(@AuthenticationPrincipal Jwt jwt,
		@PathVariable String productId) {
		return service.relatedProduct(userId(jwt), productId);
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
