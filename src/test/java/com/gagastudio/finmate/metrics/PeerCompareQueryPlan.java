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

	@Autowired
	private MonthlyRollup rollup;

	/** 채택안 — 사람×월로 미리 접어 둔 것을 읽는다 */
	private static final String ROLLUP = """
		SELECT p.income_band, count(*) AS members,
		       COALESCE(round(avg(m.spend)), 0) AS avg_spend
		FROM persona p
		LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
		GROUP BY p.income_band ORDER BY p.income_band
		""";

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

		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month CASCADE");
		importer.importFrom(bundles, 0);
		long t0 = System.nanoTime();
		rollup.rebuildAll();
		System.out.printf("%n  사전 집계 생성 %,dms · %,d행 · %s%n",
			(System.nanoTime() - t0) / 1_000_000,
			jdbc.queryForObject("SELECT count(*) FROM persona_month", Long.class),
			jdbc.queryForObject("SELECT pg_size_pretty(pg_total_relation_size(\'persona_month\'))", String.class));
		jdbc.execute("ANALYZE ledger_entry");
		jdbc.execute("ANALYZE persona");
		jdbc.execute("ANALYZE persona_month");

		var from = java.time.LocalDate.of(2026, 7, 1);
		var to = java.time.LocalDate.of(2026, 7, 31);

		for (int i = 0; i < 3; i++) {
			jdbc.queryForList(GROUP_AVG, from, to);
		}
		long[] samples = new long[40];
		for (int i = 0; i < samples.length; i++) {
			long started = System.nanoTime();
			jdbc.queryForList(GROUP_AVG, from, to);
			samples[i] = System.nanoTime() - started;
		}
		report("현재 (count DISTINCT)", GROUP_AVG, samples);

		long[] v2 = new long[40];
		for (int i = 0; i < 5; i++) {
			jdbc.queryForList(ROLLUP, from);
		}
		for (int i = 0; i < v2.length; i++) {
			long t1 = System.nanoTime();
			jdbc.queryForList(ROLLUP, from);
			v2[i] = System.nanoTime() - t1;
		}
		java.util.Arrays.sort(v2);
		reportRollup("채택안 (사전 집계)", v2, from);

		// 두 쿼리가 같은 답을 내야 개선이라 부를 수 있다
		System.out.println("\n  === 결과 대조 ===");
		var a = jdbc.queryForList(GROUP_AVG, from, to);
		var b = jdbc.queryForList(ROLLUP, from);
		for (int i = 0; i < a.size(); i++) {
			System.out.printf("    %-14s members %s/%s   avg %s/%s%n",
				a.get(i).get("income_band"), a.get(i).get("members"), b.get(i).get("members"),
				a.get(i).get("avg_spend"), b.get(i).get("avg_spend"));
		}
	}

	private void reportRollup(String label, long[] samples, java.time.LocalDate month) {
		System.out.printf("%n  === %s ===%n", label);
		System.out.printf("  p50 %6.2fms   p95 %6.2fms   max %6.2fms%n",
			samples[samples.length / 2] / 1e6,
			samples[(int) (samples.length * 0.95)] / 1e6,
			samples[samples.length - 1] / 1e6);
		jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + ROLLUP, month)
			.forEach(r -> System.out.println("    " + r.values().iterator().next()));
	}

	private void report(String label, String sql, long[] samples) {
		System.out.printf("%n  === %s ===%n", label);
		System.out.printf("  p50 %6.1fms   p95 %6.1fms   max %6.1fms%n",
			samples[samples.length / 2] / 1e6,
			samples[(int) (samples.length * 0.95)] / 1e6,
			samples[samples.length - 1] / 1e6);
		jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql,
			java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 31))
			.forEach(r -> System.out.println("    " + r.values().iterator().next()));
	}
}