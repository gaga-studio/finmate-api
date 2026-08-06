package com.gagastudio.finmate.diary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 그림일기 요청과 조회.
 *
 * 요청은 그림을 기다리지 않는다. 생성이 수 초 걸리므로 접수만 하고 202를 돌려준 뒤,
 * 화면이 조회로 상태를 따라온다. 만드는 중인지, 됐는지, 실패했다면 왜인지를 화면이 말할 수 있어야
 * "그림이 없다"가 침묵으로 끝나지 않는다.
 */
@RestController
@RequestMapping("/api/v1/personas/{personaId}/diary")
public class DiaryController {

	private final DiaryService diary;
	private final Path imageDir;

	public DiaryController(DiaryService diary, @Value("${finmate.art.dir:build/art}") String imageDir) {
		this.diary = diary;
		this.imageDir = Path.of(imageDir);
	}

	public record StatusResponse(
		LocalDate date, String status, String dominantFlow, String artStyle,
		String imageUrl, String failureReason) {
	}

	@PostMapping("/{date}")
	public ResponseEntity<StatusResponse> request(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

		if (!diary.hasLedger(personaId, date)) {
			// 거래가 없는 날은 그림도 없다. 없는 하루를 지어내지 않는다.
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "그날의 거래가 없습니다");
		}
		boolean created = diary.request(personaId, date);
		return ResponseEntity
			.status(created ? HttpStatus.ACCEPTED : HttpStatus.OK)
			.body(toResponse(personaId, date));
	}

	@GetMapping("/{date}")
	public StatusResponse status(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return toResponse(personaId, date);
	}

	@GetMapping("/{date}/image")
	public ResponseEntity<byte[]> image(
		@PathVariable UUID personaId,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

		DiaryService.Entry entry = diary.find(personaId, date)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그림일기가 없습니다"));
		if (!"READY".equals(entry.status()) || entry.imagePath() == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 준비되지 않았습니다: " + entry.status());
		}
		try {
			return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.body(Files.readAllBytes(imageDir.resolve(entry.imagePath())));
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "그림을 읽지 못했습니다");
		}
	}

	private StatusResponse toResponse(UUID personaId, LocalDate date) {
		DiaryService.Entry e = diary.find(personaId, date)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그림일기가 없습니다"));
		String url = "READY".equals(e.status())
			? "/api/v1/personas/%s/diary/%s/image".formatted(personaId, date)
			: null;
		return new StatusResponse(e.date(), e.status(), e.dominantFlow(), e.artStyle(), url, e.lastError());
	}
}
