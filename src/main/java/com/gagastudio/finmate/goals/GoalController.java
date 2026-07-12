package com.gagastudio.finmate.goals;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
class GoalController {
	private final GoalService service;

	GoalController(GoalService service) {
		this.service = service;
	}

	@GetMapping("/onboarding")
	GoalDtos.OnboardingView onboarding(@AuthenticationPrincipal Jwt jwt) {
		return service.onboarding(userId(jwt));
	}

	@PutMapping("/onboarding")
	GoalDtos.OnboardingView completeOnboarding(@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody GoalDtos.CompleteOnboardingRequest request) {
		return service.completeOnboarding(userId(jwt), idempotencyKey, request);
	}

	@GetMapping("/goals/active")
	GoalDtos.UserGoalView activeGoal(@AuthenticationPrincipal Jwt jwt) {
		return service.activeGoal(userId(jwt));
	}

	@GetMapping("/home")
	GoalDtos.HomeView home(@AuthenticationPrincipal Jwt jwt) {
		return service.home(userId(jwt));
	}

	@GetMapping("/raids/current")
	GoalDtos.RaidView currentRaid(@AuthenticationPrincipal Jwt jwt) {
		return service.currentRaid(userId(jwt));
	}

	@GetMapping("/reports/monthly")
	GoalDtos.MonthlyReportView monthlyReport(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
		return service.monthlyReport(userId(jwt), month);
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
