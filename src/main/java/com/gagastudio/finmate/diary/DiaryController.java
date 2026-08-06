package com.gagastudio.finmate.diary;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * persona를 직접 지정하는 개발용 경로.
 *
 * 앱이 쓰는 자리는 {@code /api/v1/me/diary}다. 여기는 특정 인구의 그림일기를 만들어 보거나
 * 데이터를 채울 때 쓴다. 인증을 요구하므로 아무나 남의 원장을 들여다볼 수는 없다.
 */
@RestController
@RequestMapping("/api/v1/personas/{personaId}/diary")
public class DiaryController {

	private final DiaryFacade diaries;

	public DiaryController(DiaryFacade diaries) {
		this.diaries = diaries;
	}

	public record StatusResponse(
		LocalDate date, String status, String dominantFlow, String artStyle,
		String imageUrl, String failureReason) {
	}

	@PostMapping("/{date}")
	public ResponseEntity<StatusResponse> request(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		var result = diaries.request(personaId, date);
		return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
			.body(result.status());
	}

	@GetMapping("/{date}")
	public StatusResponse status(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return diaries.status(personaId, date);
	}

	@GetMapping("/{date}/image")
	public ResponseEntity<byte[]> image(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.body(diaries.image(personaId, date));
	}
}
