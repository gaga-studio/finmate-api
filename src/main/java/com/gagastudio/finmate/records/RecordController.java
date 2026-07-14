package com.gagastudio.finmate.records;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
class RecordController {
	private final RecordService service;
	RecordController(RecordService service) { this.service = service; }
	@GetMapping
	RecordDtos.DailyRecordPage records(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate from, @RequestParam LocalDate to) {
		return service.records(UUID.fromString(jwt.getSubject()), from, to);
	}
	@GetMapping("/journey")
	RecordDtos.DailyJourneyMonthView journey(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
		return service.journey(UUID.fromString(jwt.getSubject()), month);
	}
	@GetMapping("/{date}")
	RecordDtos.DailyRecordView record(@AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date) {
		return service.record(UUID.fromString(jwt.getSubject()), date);
	}
	@PutMapping("/{date}")
	RecordDtos.DailyRecordView reflection(@AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate date,
		@Valid @RequestBody RecordDtos.SaveReflectionRequest request) {
		return service.saveReflection(UUID.fromString(jwt.getSubject()), date, request.reflection());
	}
}
