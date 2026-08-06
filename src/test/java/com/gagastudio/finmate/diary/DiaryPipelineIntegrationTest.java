package com.gagastudio.finmate.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 그림일기 파이프라인이 옳게 흐르는지 본다.
 *
 * 그림의 품질이 아니라 상태 전이를 검증한다 — 하루에 한 장인가, 실패하면 다시 시도하는가,
 * 멈춘 작업을 회수하는가, 동시에 요청해도 하나인가.
 * 진짜 생성 API는 부르지 않는다. 매번 돈이 들고 결과가 달라 무엇을 확인하는지 흐려진다.
 */
@SpringBootTest(properties = "finmate.art.provider=stub")
class DiaryPipelineIntegrationTest extends PostgresIntegrationTest {

	@Autowired private LedgerImporter importer;
	@Autowired private DiaryService diary;
	@Autowired private DiaryWorker worker;
	@Autowired private JdbcTemplate jdbc;

	private UUID persona;
	private LocalDate day;

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@BeforeEach
	void 적재한다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다. pipeline/05_generate.py를 먼저 실행하세요")
			.isTrue();
		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month, diary_entry CASCADE");
		importer.importFrom(bundlesDir(), 5);
		persona = jdbc.queryForObject("SELECT id FROM persona ORDER BY external_id LIMIT 1", UUID.class);
		day = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?", LocalDate.class, persona);
	}

	@Test
	void 요청하면_대기_제출_완료를_거쳐_그림이_생긴다() {
		assertThat(diary.request(persona, day)).isTrue();
		assertThat(diary.find(persona, day)).get().extracting(DiaryService.Entry::status).isEqualTo("PENDING");

		// 한 바퀴가 제출과 수거를 모두 시도한다. 대역이 첫 확인에 "아직"을 돌려주므로
		// 첫 바퀴는 제출만 끝나고 수거는 다음 바퀴로 넘어간다.
		DiaryWorker.Tick first = worker.tick(10);
		assertThat(first.submitted()).isEqualTo(1);
		assertThat(first.collected()).isZero();
		assertThat(diary.find(persona, day)).get().extracting(DiaryService.Entry::status).isEqualTo("SUBMITTED");

		DiaryWorker.Tick second = worker.tick(10);
		assertThat(second.collected()).isEqualTo(1);

		var entry = diary.find(persona, day).orElseThrow();
		assertThat(entry.status()).isEqualTo("READY");
		assertThat(entry.imagePath()).isNotBlank();
		assertThat(Path.of("build/art", entry.imagePath())).exists();
	}

	@Test
	void 같은_날을_두_번_요청해도_그림은_하나다() {
		assertThat(diary.request(persona, day)).isTrue();
		assertThat(diary.request(persona, day)).isFalse();

		Long rows = jdbc.queryForObject(
			"SELECT count(*) FROM diary_entry WHERE persona_id = ? AND entry_date = ?",
			Long.class, persona, day);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	void 동시에_요청해도_그림은_하나다() throws Exception {
		// 애플리케이션에서 "있는지 보고 없으면 넣는다"로 했으면 여기서 두 건이 들어간다
		try (var pool = Executors.newFixedThreadPool(8)) {
			List<Callable<Boolean>> calls = java.util.Collections.nCopies(8,
				() -> diary.request(persona, day));
			long created = pool.invokeAll(calls).stream().filter(f -> {
				try {
					return f.get();
				} catch (Exception e) {
					return false;
				}
			}).count();
			assertThat(created).isEqualTo(1);
		}

		Long rows = jdbc.queryForObject(
			"SELECT count(*) FROM diary_entry WHERE persona_id = ? AND entry_date = ?",
			Long.class, persona, day);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	void 실패하면_세_번까지_다시_시도하고_이유를_남긴다() {
		diary.request(persona, day);
		// 대역이 실패로 끝내도록 프롬프트에 표식을 심는다
		jdbc.update("UPDATE diary_entry SET prompt = prompt || ? WHERE persona_id = ?",
			StubArtProvider.FAIL_MARKER, persona);

		for (int i = 0; i < 6; i++) {
			worker.tick(10);
		}

		var entry = diary.find(persona, day).orElseThrow();
		assertThat(entry.status()).isEqualTo("FAILED");
		assertThat(entry.attempts()).isEqualTo(3);
		// 왜 그림이 없는지 화면이 말할 수 있어야 한다
		assertThat(entry.lastError()).contains("STUB_FAILURE");
	}

	@Test
	void 응답이_안_오는_작업을_되돌린다() {
		diary.request(persona, day);
		worker.tick(10);
		assertThat(diary.find(persona, day)).get().extracting(DiaryService.Entry::status).isEqualTo("SUBMITTED");

		// 제출한 지 오래된 것처럼 만든다
		jdbc.update("UPDATE diary_entry SET submitted_at = now() - interval '10 minutes' WHERE persona_id = ?",
			persona);

		DiaryWorker.Tick tick = worker.tick(10);
		assertThat(tick.reclaimed()).isEqualTo(1);
		// 되돌린 직후 같은 바퀴에서 다시 제출된다
		assertThat(diary.find(persona, day)).get().extracting(DiaryService.Entry::status).isEqualTo("SUBMITTED");
	}

	@Test
	void 그림체는_날짜에서_정해져_다시_만들어도_같다() {
		// 무작위로 골랐다면 지웠다 다시 만들 때 과거의 그림체가 바뀐다
		ArtStyle first = ArtStyle.forDate(day);
		ArtStyle again = ArtStyle.forDate(day);
		assertThat(first).isEqualTo(again);

		// 엿새면 여섯 가지가 모두 나온다 (로우폴리는 모델이 못 그려 뺐다)
		var cycle = java.util.stream.IntStream.range(0, ArtStyle.values().length)
			.mapToObj(i -> ArtStyle.forDate(day.plusDays(i)))
			.distinct().toList();
		assertThat(cycle).hasSize(ArtStyle.values().length);
	}

	@Test
	void 그날의_주인공이_원장에서_나온다() {
		diary.request(persona, day);
		var entry = diary.find(persona, day).orElseThrow();

		String biggestFlow = jdbc.queryForObject("""
			SELECT flow FROM ledger_entry
			WHERE persona_id = ? AND occurred_on = ? AND flow IN ('소비','저축','투자')
			GROUP BY flow ORDER BY -sum(amount) DESC LIMIT 1
			""", String.class, persona, day);
		assertThat(entry.dominantFlow()).isEqualTo(biggestFlow);
	}

	@Test
	void 프롬프트에_가맹점_실명이_들어가지_않는다() {
		diary.request(persona, day);
		String prompt = jdbc.queryForObject(
			"SELECT prompt FROM diary_entry WHERE persona_id = ?", String.class, persona);

		List<String> merchants = jdbc.queryForList(
			"SELECT DISTINCT merchant FROM ledger_entry WHERE persona_id = ? AND occurred_on = ?",
			String.class, persona, day);
		// 실명 상표가 그림에 글자로 새겨지는 것을 막는다
		assertThat(merchants).isNotEmpty().allSatisfy(m -> assertThat(prompt).doesNotContain(m));
	}
}
