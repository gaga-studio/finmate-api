package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 개인 원장 조회는 빨랐다. 그럼 비싼 건 어디인가 — 또래 비교를 재 본다.
 *
 * 개인 조회는 한 사람의 한 달(수십 행)만 읽는다. 또래 비교는 소득대가 같은 사람 전부를
 * 가로질러 집계한다. 접근 범위가 다르므로 결과도 다를 것이다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FINMATE_FULL_IMPORT", matches = "1")
class PeerCompareQueryPlan extends PostgresIntegrationTest {

	@Autowired
	private LedgerImporter importer;

	@Autowired
	private JdbcTemplate jdbc;

	private static final String GROUP_AVG = """
		SELECT p.income_band,
		       count(DISTINCT p.id) AS members,
		       COALESCE(-sum(e.amount) FILTER (WHERE e.flow = '소비'), 0) / count(DISTINCT p.id) AS avg_spend
		FROM persona p
		JOIN ledger_entry e ON e.persona_id = p.id
		WHERE e.occurred_on BETWEEN ? AND ?
		GROUP BY p.income_band
		ORDER BY p.income_band
		""";

	@Test
	void 또래_비교를_잰다() {
		Path bundles = Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
		assumeThat(Files.isDirectory(bundles)).isTrue();

		jdbc.execute("TRUNCATE ledger_entry, persona CASCADE");
		importer.importFrom(bundles, 0);
		jdbc.execute("ANALYZE ledger_entry");
		jdbc.execute("ANALYZE persona");

		var from = java.time.LocalDate.of(2026, 7, 1);
		var to = java.time.LocalDate.of(2026, 7, 31);

		for (int i = 0; i < 3; i++) {
			jdbc.queryForList(GROUP_AVG, from, to);
		}
		long[] samples = new long[20];
		for (int i = 0; i < samples.length; i++) {
			long t0 = System.nanoTime();
			jdbc.queryForList(GROUP_AVG, from, to);
			samples[i] = System.nanoTime() - t0;
		}
		java.util.Arrays.sort(samples);
		System.out.printf("%n  === 또래 비교 (소득대별 평균 소비, 2,000명 전체) ===%n");
		System.out.printf("  p50 %6.1fms   p95 %6.1fms   max %6.1fms%n",
			samples[samples.length / 2] / 1e6,
			samples[(int) (samples.length * 0.95)] / 1e6,
			samples[samples.length - 1] / 1e6);

		System.out.println("\n  === 실행 계획 ===");
		jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + GROUP_AVG, from, to)
			.forEach(r -> System.out.println("    " + r.values().iterator().next()));
	}
}
