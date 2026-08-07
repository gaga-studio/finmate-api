package com.gagastudio.finmate.diary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 그림일기를 HTTP 바깥에서 쓸 수 있게 모아 둔다.
 *
 * 컨트롤러가 둘이 됐다 — persona를 경로로 받는 개발용과, 토큰에서 꺼내는 실사용용.
 * 같은 로직이 양쪽에 복사되면 한쪽만 고치는 일이 생긴다. 응답 형태를 만드는 일까지 여기로 옮긴다.
 */
@Component
public class DiaryFacade {

	private final DiaryService diary;
	private final Path imageDir;

	public DiaryFacade(DiaryService diary, @Value("${finmate.art.dir:build/art}") String imageDir) {
		this.diary = diary;
		this.imageDir = Path.of(imageDir);
	}

	public record RequestResult(boolean created, DiaryController.StatusResponse status) {
	}

	public RequestResult request(UUID personaId, LocalDate date) {
		if (!diary.hasLedger(personaId, date)) {
			// 거래가 없는 날은 그림도 없다. 없는 하루를 지어내지 않는다.
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "그날의 거래가 없습니다");
		}
		boolean created = diary.request(personaId, date);
		return new RequestResult(created, status(personaId, date));
	}

	public DiaryController.StatusResponse status(UUID personaId, LocalDate date) {
		DiaryService.Entry e = diary.find(personaId, date)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그림일기가 없습니다"));
		String url = "READY".equals(e.status()) ? "/api/v1/me/diary/%s/image".formatted(date) : null;
		return new DiaryController.StatusResponse(
			e.date(), e.status(), e.dominantFlow(), e.artStyle(), url, e.lastError());
	}

	public byte[] image(UUID personaId, LocalDate date) {
		DiaryService.Entry e = diary.find(personaId, date)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그림일기가 없습니다"));
		if (!"READY".equals(e.status()) || e.imagePath() == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 준비되지 않았습니다: " + e.status());
		}
		try {
			return Files.readAllBytes(imageDir.resolve(e.imagePath()));
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "그림을 읽지 못했습니다");
		}
	}
}
