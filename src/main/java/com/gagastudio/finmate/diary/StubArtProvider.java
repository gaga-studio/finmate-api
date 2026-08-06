package com.gagastudio.finmate.diary;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 키 없이도 파이프라인이 돌게 하는 대역.
 *
 * 테스트가 진짜 API를 부르면 매번 돈이 들고, 결과가 매번 달라서 무엇을 검증하는지도 흐려진다.
 * 여기서 확인하려는 건 그림의 품질이 아니라 **상태가 옳게 흐르는가**다 —
 * 하루에 한 장인가, 실패하면 다시 시도하는가, 중간에 멈춘 작업을 회수하는가.
 *
 * 즉시 끝내지 않고 한 번은 "아직"을 돌려준다. 바로 완료되면 폴링 경로가 한 번도 실행되지 않아
 * 정작 검증하려던 비동기 흐름이 테스트를 빠져나간다.
 */
@Component
@ConditionalOnProperty(name = "finmate.art.provider", havingValue = "stub", matchIfMissing = true)
public class StubArtProvider implements ArtProvider {

	private final Map<String, Integer> polls = new ConcurrentHashMap<>();

	/** 테스트가 실패를 재현할 수 있게 열어 둔다. 이 문자열이 프롬프트에 있으면 실패로 끝난다. */
	public static final String FAIL_MARKER = "__fail__";

	@Override
	public String submit(String prompt) {
		String id = UUID.randomUUID().toString();
		if (prompt.contains(FAIL_MARKER)) {
			polls.put(id, Integer.MIN_VALUE);
		} else {
			polls.put(id, 0);
		}
		return id;
	}

	@Override
	public Optional<byte[]> poll(String jobId) {
		Integer n = polls.get(jobId);
		if (n == null) {
			throw new ArtGenerationException("모르는 작업입니다: " + jobId);
		}
		if (n == Integer.MIN_VALUE) {
			throw new ArtGenerationException("생성이 실패했습니다: STUB_FAILURE");
		}
		if (n == 0) {
			polls.put(jobId, 1);
			return Optional.empty();
		}
		return Optional.of(placeholder());
	}

	private byte[] placeholder() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
		var g = img.createGraphics();
		g.setColor(new Color(0x1f, 0x9d, 0x8f));
		g.fillRect(0, 0, 64, 64);
		g.dispose();
		try (var out = new ByteArrayOutputStream()) {
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
