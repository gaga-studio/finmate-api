package com.gagastudio.finmate.diary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * fal.ai 큐에 그림 생성을 맡긴다.
 *
 * 동기 엔드포인트(fal.run)도 있지만 큐(queue.fal.run)를 쓴다. 생성이 3~6초라
 * 동기로 부르면 그동안 스레드가 잡히고, 무엇보다 서버가 재시작하면 진행 중이던 작업을
 * 잃는다. 큐는 작업 id를 주므로 재시작 후에도 결과를 다시 찾을 수 있다.
 *
 * API 키는 환경변수로만 받는다. 키가 없으면 이 빈이 아예 만들어지지 않고
 * {@link StubArtProvider}가 대신 들어간다 — 키 없이도 서버가 뜨고 테스트가 돌아야 한다.
 *
 * 조회 주소를 직접 조립하지 않는다. 제출은 {@code fal-ai/flux/dev}로 하는데 조회 주소는
 * {@code fal-ai/flux/requests/{id}}로 내려온다 — 모델 경로에서 변형(dev)이 빠진다.
 * 그걸 모르고 조립했다가 405를 받았다. 제공자가 준 주소를 그대로 들고 다니는 쪽이
 * 짧기도 하고, 제공자가 주소 규칙을 바꿔도 깨지지 않는다.
 */
@Component
@ConditionalOnProperty(name = "finmate.art.provider", havingValue = "fal")
public class FalArtProvider implements ArtProvider {

	private static final Logger log = LoggerFactory.getLogger(FalArtProvider.class);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String MODEL = "fal-ai/flux/dev";

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private final String apiKey;

	public FalArtProvider(org.springframework.core.env.Environment env) {
		this.apiKey = env.getProperty("FAL_KEY", "");
		if (apiKey.isBlank()) {
			throw new IllegalStateException("FAL_KEY가 없습니다. .env에 넣거나 finmate.art.provider를 stub으로 두세요.");
		}
	}

	/**
	 * 작업 손잡이. 상태와 결과 주소를 제공자에게서 받은 그대로 들고 있는다.
	 * DB에는 두 주소를 개행으로 이어 한 칸에 넣는다 — 컬럼을 둘로 나눌 만큼의 값이 아니다.
	 */
	record JobHandle(String statusUrl, String responseUrl) {
		String serialize() {
			return statusUrl + "\n" + responseUrl;
		}

		static JobHandle parse(String raw) {
			String[] parts = raw.split("\n", 2);
			if (parts.length != 2) {
				throw new ArtGenerationException("작업 손잡이를 읽지 못했습니다");
			}
			return new JobHandle(parts[0], parts[1]);
		}
	}

	@Override
	public String submit(String prompt) {
		String body;
		try {
			body = JSON.writeValueAsString(java.util.Map.of(
				"prompt", prompt,
				"image_size", "square_hd",
				"num_images", 1));
		} catch (IOException e) {
			throw new ArtGenerationException("요청 본문을 만들지 못했습니다", e);
		}

		JsonNode json = send(HttpRequest.newBuilder(URI.create("https://queue.fal.run/" + MODEL))
			.header("Authorization", "Key " + apiKey)
			.header("content-type", "application/json")
			.timeout(Duration.ofSeconds(20))
			.POST(HttpRequest.BodyPublishers.ofString(body)));

		String statusUrl = json.path("status_url").asText("");
		String responseUrl = json.path("response_url").asText("");
		if (statusUrl.isBlank() || responseUrl.isBlank()) {
			throw new ArtGenerationException("작업 주소를 받지 못했습니다");
		}
		return new JobHandle(statusUrl, responseUrl).serialize();
	}

	@Override
	public Optional<byte[]> poll(String jobId) {
		JobHandle job = JobHandle.parse(jobId);
		JsonNode status = send(HttpRequest.newBuilder(URI.create(job.statusUrl()))
			.header("Authorization", "Key " + apiKey)
			.timeout(Duration.ofSeconds(15))
			.GET());

		String state = status.path("status").asText("");
		switch (state) {
			case "IN_QUEUE", "IN_PROGRESS" -> {
				return Optional.empty();
			}
			case "COMPLETED" -> {
				JsonNode result = send(HttpRequest.newBuilder(URI.create(job.responseUrl()))
					.header("Authorization", "Key " + apiKey)
					.timeout(Duration.ofSeconds(15))
					.GET());
				String url = result.path("images").path(0).path("url").asText("");
				if (url.isBlank()) {
					throw new ArtGenerationException("완료됐는데 이미지 주소가 없습니다");
				}
				return Optional.of(download(url));
			}
			default -> throw new ArtGenerationException("생성이 실패했습니다: " + state);
		}
	}

	private JsonNode send(HttpRequest.Builder request) {
		try {
			HttpResponse<String> res = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() >= 400) {
				// 본문에 키가 실릴 일은 없지만, 그래도 상태 코드까지만 남긴다
				throw new ArtGenerationException("제공자가 " + res.statusCode() + "를 돌려줬습니다");
			}
			return JSON.readTree(res.body());
		} catch (IOException e) {
			throw new ArtGenerationException("제공자를 부르지 못했습니다", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArtGenerationException("호출이 중단됐습니다", e);
		}
	}

	private byte[] download(String url) {
		try {
			HttpResponse<byte[]> res = http.send(
				HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() >= 400) {
				throw new ArtGenerationException("이미지를 받지 못했습니다: " + res.statusCode());
			}
			log.debug("그림 {}KB 수신", res.body().length / 1024);
			return res.body();
		} catch (IOException e) {
			throw new ArtGenerationException("이미지를 받지 못했습니다", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArtGenerationException("수신이 중단됐습니다", e);
		}
	}
}
