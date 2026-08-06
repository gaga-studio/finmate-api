package com.gagastudio.finmate.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 진짜 생성 API로 파이프라인 전체를 한 번 돌린다.
 *
 * 평소 테스트에는 넣지 않는다 — 호출마다 돈이 들고 결과가 매번 다르다.
 * 대역으로는 확인할 수 없는 것만 여기서 본다: 실제 제공자의 응답 형태가 우리 가정과 맞는가,
 * 그림이 정말 파일로 떨어지는가.
 *
 * <pre>
 *   FAL_KEY=... FINMATE_REAL_ART=1 ./gradlew test --tests '*RealFalGenerationTest'
 * </pre>
 */
@SpringBootTest(properties = "finmate.art.provider=fal")
@EnabledIfEnvironmentVariable(named = "FINMATE_REAL_ART", matches = "1")
class RealFalGenerationTest extends PostgresIntegrationTest {

	@Autowired private LedgerImporter importer;
	@Autowired private DiaryService diary;
	@Autowired private DiaryWorker worker;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void 실제로_그림이_만들어진다() throws Exception {
		Path bundles = Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
		assumeThat(Files.isDirectory(bundles)).isTrue();

		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month, diary_entry CASCADE");
		importer.importFrom(bundles, 3);

		UUID persona = jdbc.queryForObject(
			"SELECT id FROM persona ORDER BY external_id LIMIT 1", UUID.class);
		LocalDate day = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?", LocalDate.class, persona);

		diary.request(persona, day);
		var requested = diary.find(persona, day).orElseThrow();
		System.out.printf("%n  그날의 주인공: %s / %s · 그림체 %s%n",
			requested.dominantFlow(), requested.subject(), requested.artStyle());
		System.out.println("  프롬프트: " + jdbc.queryForObject(
			"SELECT prompt FROM diary_entry WHERE persona_id = ?", String.class, persona));

		long t0 = System.nanoTime();
		DiaryService.Entry entry = null;
		for (int i = 0; i < 40; i++) {
			worker.tick(5);
			entry = diary.find(persona, day).orElseThrow();
			if (!entry.status().equals("PENDING") && !entry.status().equals("SUBMITTED")) {
				break;
			}
			Thread.sleep(1500);
		}

		System.out.printf("  결과: %s · %,dms%n", entry.status(), (System.nanoTime() - t0) / 1_000_000);
		assertThat(entry.status()).isEqualTo("READY");

		Path image = Path.of("build/art", entry.imagePath());
		assertThat(image).exists();
		System.out.printf("  그림: %s · %,dKB%n", image, Files.size(image) / 1024);
	}
}
