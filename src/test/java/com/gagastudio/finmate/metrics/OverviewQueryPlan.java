package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 전체 2,000명(89만 행)을 넣은 뒤 마이 탭 조회의 실행 계획과 소요를 찍는다.
 *
 * 고치기 전에 재려는 것이다. 개선 후 숫자만 있으면 무엇이 좋아졌는지 말할 수 없다.
 *
 * <pre>
 *   FINMATE_FULL_IMPORT=1 ./gradlew test --tests '*OverviewQueryPlan'
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FINMATE_FULL_IMPORT", matches = "1")
class OverviewQueryPlan extends PostgresIntegrationTest {

	@Autowired
	private LedgerImporter importer;

	@Autowired
	private OverviewService overviews;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 계획과_소요를_찍는다() {
		Path bundles = Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
		assumeThat(Files.isDirectory(bundles)).isTrue();

		jdbc.execute("TRUNCATE ledger_entry, persona CASCADE");
		importer.importFrom(bundles, 0);
		jdbc.execute("ANALYZE ledger_entry");
		jdbc.execute("ANALYZE persona");

		Long rows = jdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class);
		System.out.printf("%n  === 원장 %,d행 ===%n", rows);

		List<UUID> personas = jdbc.queryForList(
			"SELECT id FROM persona ORDER BY external_id LIMIT 200", UUID.class);

		for (PeriodType period : PeriodType.values()) {
			// 워밍업
			for (int i = 0; i < 20; i++) {
				overviews.of(personas.get(i % personas.size()), period);
			}
			long[] samples = new long[200];
			for (int i = 0; i < samples.length; i++) {
				long t0 = System.nanoTime();
				overviews.of(personas.get(i % personas.size()), period);
				samples[i] = System.nanoTime() - t0;
			}
			java.util.Arrays.sort(samples);
			System.out.printf("  %-8s p50 %6.2fms   p95 %6.2fms   max %6.2fms%n",
				period.wireName(),
				samples[samples.length / 2] / 1e6,
				samples[(int) (samples.length * 0.95)] / 1e6,
				samples[samples.length - 1] / 1e6);
		}

		UUID one = personas.get(0);
		var range = PeriodType.MONTHLY.range(
			jdbc.queryForObject("SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?",
				java.time.LocalDate.class, one));
		System.out.println("\n  === 월간 집계 실행 계획 ===");
		jdbc.queryForList("""
			EXPLAIN (ANALYZE, BUFFERS)
			SELECT
			  COALESCE(-sum(amount) FILTER (WHERE flow = '소비'), 0),
			  COALESCE(-sum(amount) FILTER (WHERE flow = '저축'), 0),
			  COALESCE(-sum(amount) FILTER (WHERE flow = '투자'), 0),
			  COALESCE( sum(amount) FILTER (WHERE flow = '소득'), 0)
			FROM ledger_entry
			WHERE persona_id = ? AND occurred_on BETWEEN ? AND ?
			""", one, range.start(), range.end())
			.forEach(r -> System.out.println("    " + r.values().iterator().next()));
	}
}
