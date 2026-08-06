package com.gagastudio.finmate.diary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 그림일기 작업을 진행시킨다.
 *
 * 상태는 넷이다.
 *
 * <pre>
 *   PENDING  ──제출──▶ SUBMITTED ──수거──▶ READY
 *      ▲                   │                │
 *      └───재시도·회수───────┴──횟수 초과──▶ FAILED
 * </pre>
 *
 * 바깥 세계에 기대는 일이라 실패가 정상이다. 다루는 방식이 셋이다 —
 * <b>재시도</b>(일시적 실패), <b>회수</b>(제출은 됐는데 응답이 안 오는 작업),
 * <b>포기</b>(횟수를 넘기면 이유를 남기고 멈춘다).
 *
 * 여러 인스턴스가 떠도 한 작업을 두 번 하지 않는다 — 할 일을 잠금이 아니라 <b>상태로</b> 집는다.
 * {@link #claimPending(int)} 주석 참고.
 *
 * 각 단계는 SQL 한 문장이라 그 자체로 원자적이다. 그래서 여기에는 트랜잭션 경계를 두지 않는다.
 * 두더라도 {@code tick}이 같은 객체의 메서드를 부르는 구조라 프록시를 타지 않아 걸리지 않는다 —
 * 붙어 있으면 걸린다고 오해하게 되므로 아예 빼 두었다.
 */
@Component
public class DiaryWorker {

	private static final Logger log = LoggerFactory.getLogger(DiaryWorker.class);

	/** 이보다 오래 SUBMITTED에 머문 작업은 응답이 오지 않는 것으로 보고 되돌린다. */
	private static final Duration STUCK_AFTER = Duration.ofMinutes(5);
	private static final int MAX_ATTEMPTS = 3;

	private final JdbcTemplate jdbc;
	private final ArtProvider provider;
	private final Path imageDir;

	public DiaryWorker(JdbcTemplate jdbc, ArtProvider provider,
		@Value("${finmate.art.dir:build/art}") String imageDir) {
		this.jdbc = jdbc;
		this.provider = provider;
		this.imageDir = Path.of(imageDir);
	}

	public record Tick(int submitted, int collected, int failed, int reclaimed) {
	}

	/** 한 바퀴 돈다. 스케줄러가 부르거나 테스트가 직접 부른다. */
	public Tick tick(int batch) {
		int reclaimed = reclaimStuck();
		int submitted = 0;
		int collected = 0;
		int failed = 0;

		for (Map<String, Object> row : claimPending(batch)) {
			if (submitOne(row)) {
				submitted++;
			} else {
				failed++;
			}
		}
		for (Map<String, Object> row : claimSubmitted(batch)) {
			switch (collectOne(row)) {
				case READY -> collected++;
				case FAILED -> failed++;
				case WAITING -> {
				}
			}
		}
		if (submitted + collected + failed + reclaimed > 0) {
			log.info("그림일기 — 제출 {} · 수거 {} · 실패 {} · 회수 {}", submitted, collected, failed, reclaimed);
		}
		return new Tick(submitted, collected, failed, reclaimed);
	}

	/**
	 * 할 일을 집는다.
	 *
	 * 처음엔 SELECT ... FOR UPDATE SKIP LOCKED로 잠그고, 그 다음 별도 트랜잭션에서
	 * 외부 API를 불렀다. **아무 소용이 없었다** — 잠금은 그 트랜잭션이 끝나면 풀리므로,
	 * 정작 외부 호출이 도는 동안에는 아무도 그 행을 지키고 있지 않았다.
	 *
	 * 그렇다고 잠근 채로 외부 API를 부를 수도 없다. 3~6초 동안 DB 커넥션을 붙들고 있게 된다.
	 *
	 * 그래서 잠금이 아니라 **상태로 집는다.** UPDATE 한 번에 PENDING을 SUBMITTED로 바꾸면서
	 * 가져온다. 이 UPDATE는 원자적이라 두 인스턴스가 같은 행을 가져갈 수 없다.
	 * SKIP LOCKED는 서로 다른 행을 동시에 집을 때 기다리지 않게 하는 역할만 한다.
	 *
	 * 대신 집은 직후에 죽으면 job id 없는 SUBMITTED가 남는다 — {@link #reclaimStuck()}이 처리한다.
	 */
	List<Map<String, Object>> claimPending(int limit) {
		return jdbc.queryForList("""
			UPDATE diary_entry SET status = 'SUBMITTED', attempts = attempts + 1, submitted_at = now()
			WHERE id IN (
			  SELECT id FROM diary_entry WHERE status = 'PENDING'
			  ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED
			)
			RETURNING id, prompt, attempts
			""", limit);
	}

	/**
	 * 수거 대상은 job id가 있는 SUBMITTED뿐이다.
	 *
	 * 상태만 바뀌고 제출 전에 죽은 행은 job id가 없다. 그걸 수거하려 들면
	 * "모르는 작업"으로 실패 처리되어 멀쩡한 작업을 버리게 된다. 회수에 맡긴다.
	 */
	List<Map<String, Object>> claimSubmitted(int limit) {
		return jdbc.queryForList("""
			SELECT id, prompt, provider_job_id, attempts
			FROM diary_entry
			WHERE status = 'SUBMITTED' AND provider_job_id IS NOT NULL
			ORDER BY created_at LIMIT ?
			""", limit);
	}

	boolean submitOne(Map<String, Object> row) {
		long id = ((Number) row.get("id")).longValue();
		int attempts = ((Number) row.get("attempts")).intValue();
		try {
			// claimPending이 이미 SUBMITTED로 바꾸고 attempts를 올렸다. 여기서는 job id만 채운다.
			String jobId = provider.submit((String) row.get("prompt"));
			jdbc.update("UPDATE diary_entry SET provider_job_id = ?, last_error = NULL WHERE id = ?",
				jobId, id);
			return true;
		} catch (RuntimeException e) {
			giveUpOrRetry(id, attempts, e);
			return false;
		}
	}

	private enum Collected {
		READY, FAILED, WAITING
	}

	Collected collectOne(Map<String, Object> row) {
		long id = ((Number) row.get("id")).longValue();
		int attempts = ((Number) row.get("attempts")).intValue();
		String jobId = (String) row.get("provider_job_id");
		try {
			Optional<byte[]> image = provider.poll(jobId);
			if (image.isEmpty()) {
				return Collected.WAITING;
			}
			String path = store(id, image.get());
			jdbc.update("""
				UPDATE diary_entry SET status = 'READY', image_path = ?, completed_at = now(),
				  last_error = NULL
				WHERE id = ?
				""", path, id);
			return Collected.READY;
		} catch (RuntimeException e) {
			giveUpOrRetry(id, attempts, e);
			return Collected.FAILED;
		}
	}

	/**
	 * 실패를 어떻게 다룰지 정한다.
	 *
	 * 횟수가 남았으면 PENDING으로 되돌려 다음 바퀴에 다시 시도한다. 넘었으면 FAILED로 굳히고
	 * 이유를 남긴다 — 조용히 사라지면 왜 그림이 없는지 아무도 모른다.
	 */
	private void giveUpOrRetry(long id, int attempts, RuntimeException e) {
		String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		if (reason.length() > 500) {
			reason = reason.substring(0, 500);
		}
		if (attempts >= MAX_ATTEMPTS) {
			jdbc.update("""
				UPDATE diary_entry SET status = 'FAILED', last_error = ?, attempts = ?,
				  completed_at = now()
				WHERE id = ?
				""", reason, attempts, id);
			log.warn("그림일기 {} 포기 — {}", id, reason);
		} else {
			jdbc.update("""
				UPDATE diary_entry SET status = 'PENDING', last_error = ?, attempts = ?,
				  provider_job_id = NULL, submitted_at = NULL
				WHERE id = ?
				""", reason, attempts, id);
		}
	}

	/**
	 * 제출은 됐는데 응답이 오지 않는 작업을 되돌린다.
	 *
	 * 두 경우를 함께 처리한다 —
	 * 제출은 됐는데 제공자가 응답을 안 주는 경우(job id 있음),
	 * 집은 직후 제출 전에 우리가 죽은 경우(job id 없음).
	 * 둘 다 두면 화면이 "만드는 중"을 영원히 보여준다.
	 */
	int reclaimStuck() {
		// 기준 시각을 애플리케이션에서 만들어 넘기지 않는다. JDBC가 Instant를 바인딩하지 못하는
		// 문제도 있지만, 더 중요한 건 서버 시계와 DB 시계가 어긋나면 회수가 너무 빨라지거나
		// 영영 안 일어난다는 점이다. DB의 now()를 기준으로 삼는다.
		return jdbc.update("""
			UPDATE diary_entry SET status = 'PENDING', provider_job_id = NULL, submitted_at = NULL,
			  last_error = '응답이 오지 않아 되돌렸습니다'
			WHERE status = 'SUBMITTED'
			  AND submitted_at < now() - make_interval(secs => ?)
			  AND attempts < ?
			""", (double) STUCK_AFTER.toSeconds(), MAX_ATTEMPTS);
	}

	private String store(long id, byte[] image) {
		try {
			Files.createDirectories(imageDir);
			String name = "diary-%d.png".formatted(id);
			Files.write(imageDir.resolve(name), image);
			return name;
		} catch (IOException e) {
			throw new UncheckedIOException("그림을 저장하지 못했습니다", e);
		}
	}
}
