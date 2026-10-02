package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/** Opt-in reproducible SQL experiment, separate from normal correctness tests. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FINMATE_BENCHMARK", matches = "1")
class PeerCompareQueryPlan extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerImporter importer;
    @Autowired MonthlyRollup rollup;
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);
    private static final int WARMUP = 5;
    private static final int REPEATS = 40;

    @Test
    void compareEquivalentAlternativesAndKeepEverySample() throws Exception {
        jdbc.execute("TRUNCATE persona CASCADE");
        importer.importFrom(Path.of("demo", "bundles"), 0);
        jdbc.update("DELETE FROM ledger_entry");
        // 2,000 complete profiles, 1,800 spending profiles, 6 months × 40 rows.
        jdbc.update("""
            INSERT INTO persona SELECT md5('bench-' || n)::uuid, 'B' || n, '합성 ' || n,
              27, 'MZ', '직장인', '계획형', '서울', '1인', 1, 3000000,
              'band-' || (n % 4), '정기', 1200000, 0.2, 0.1, true, 3, '중립',
              '2026-01-01'::date, '2026-07-31'::date
            FROM generate_series(25, 2000) n
            """);
        jdbc.update("UPDATE persona SET income_band = 'band-' || (substring(external_id FROM 2)::int % 4)");
        jdbc.update("""
            INSERT INTO ledger_entry(persona_id, external_id, occurred_at, merchant, amount, category, flow, major, minor)
            SELECT p.id, 'M' || m || '-T' || t,
              make_date(2026, m, (t % 28) + 1)::timestamp, '합성 가맹점', -(1000 + t * 101),
              'food', '소비', '식비', '외식'
            FROM persona p CROSS JOIN generate_series(2, 7) m CROSS JOIN generate_series(1, 40) t
            WHERE substring(p.external_id FROM 2)::int % 10 <> 0
            """);
        long rebuildStarted = System.nanoTime();
        rollup.rebuildAll();
        long rebuildNanos = System.nanoTime() - rebuildStarted;
        jdbc.execute("ANALYZE persona; ANALYZE ledger_entry; ANALYZE persona_month");
        var expected = jdbc.queryForList(PeerQueryAlternatives.DISTINCT, FROM, TO);
        Map<String, String> alternatives = new LinkedHashMap<>();
        alternatives.put("distinct", PeerQueryAlternatives.DISTINCT);
        alternatives.put("rewritten", PeerQueryAlternatives.REWRITTEN);
        alternatives.put("rollup", PeerQueryAlternatives.ROLLUP);
        Path output = Path.of("build", "evidence", "peer-comparison");
        Files.createDirectories(output);
        StringBuilder samples = new StringBuilder("variant,iteration,elapsed_ns\n");
        StringBuilder summary = new StringBuilder("variant,p50_ms,p95_ms,max_ms\n");
        for (var entry : alternatives.entrySet()) {
            assertThat(jdbc.queryForList(entry.getValue(), FROM, TO)).isEqualTo(expected);
        }
        Map<String, long[]> raw = new LinkedHashMap<>();
        alternatives.forEach((name, sql) -> raw.put(name, new long[REPEATS]));
        for (int i = -WARMUP; i < REPEATS; i++) {
            for (var entry : alternatives.entrySet()) {
                long started = System.nanoTime();
                var result = jdbc.queryForList(entry.getValue(), FROM, TO);
                long elapsed = System.nanoTime() - started;
                assertThat(result).isEqualTo(expected);
                if (i >= 0) raw.get(entry.getKey())[i] = elapsed;
            }
        }
        for (var entry : alternatives.entrySet()) {
            record(output, samples, summary, entry.getKey(), entry.getValue(), raw.get(entry.getKey()));
        }
        jdbc.execute("CREATE INDEX benchmark_covering ON ledger_entry(flow, occurred_on) INCLUDE (persona_id, amount)");
        jdbc.execute("VACUUM ANALYZE ledger_entry");
        long[] indexed = new long[REPEATS];
        for (int i = -WARMUP; i < REPEATS; i++) {
            long started = System.nanoTime();
            var result = jdbc.queryForList(PeerQueryAlternatives.REWRITTEN, FROM, TO);
            long elapsed = System.nanoTime() - started;
            assertThat(result).isEqualTo(expected);
            if (i >= 0) indexed[i] = elapsed;
        }
        record(output, samples, summary, "rewritten_covering_index", PeerQueryAlternatives.REWRITTEN, indexed);
        Files.writeString(output.resolve("samples.csv"), samples);
        Files.writeString(output.resolve("summary.csv"), summary);
        var git = new ProcessBuilder("git", "rev-parse", "HEAD").start();
        String revision = new String(git.getInputStream().readAllBytes()).trim();
        String diff = new String(new ProcessBuilder("git", "diff", "--binary").start().getInputStream().readAllBytes());
        Files.writeString(output.resolve("tracked-changes.patch"), diff);
        Files.writeString(output.resolve("environment.txt"), """
            recorded_at=%s
            base_commit=%s
            working_tree=uncommitted changes; see tracked-changes.patch and source files
            java=%s
            os=%s %s
            postgres=%s
            personas=%s
            ledger_rows=%s
            warmup_per_variant=%d
            measured_per_variant=%d
            failures=0 (any query/result mismatch aborts experiment)
            percentile=nearest-rank on a sorted copy, ceil(p*n)-1
            measurement=client JDBC round trip in ns, not HTTP latency
            server_execution_time=single EXPLAIN ANALYZE observation in each plan file
            ordering=distinct/rewritten/rollup interleaved; covering index measured afterward
            cache=warm; index phase additionally runs VACUUM ANALYZE
            rebuild_all_ms=%.3f
            rollup_bytes=%s
            covering_index_bytes=%s
            """.formatted(java.time.Instant.now(), revision, System.getProperty("java.version"),
            System.getProperty("os.name"), System.getProperty("os.arch"),
            jdbc.queryForObject("SELECT version()", String.class),
            jdbc.queryForObject("SELECT count(*) FROM persona", Long.class),
            jdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class), WARMUP, REPEATS,
            rebuildNanos / 1e6, jdbc.queryForObject("SELECT pg_total_relation_size('persona_month')", Long.class),
            jdbc.queryForObject("SELECT pg_total_relation_size('benchmark_covering')", Long.class)));
        System.out.println(summary);
    }

    private void record(Path output, StringBuilder raw, StringBuilder summary, String name, String sql, long[] samples) throws Exception {
        for (int i = 0; i < samples.length; i++) raw.append(name).append(',').append(i).append(',').append(samples[i]).append('\n');
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        summary.append(String.format(java.util.Locale.ROOT, "%s,%.6f,%.6f,%.6f%n", name,
            sorted[(int) Math.ceil(sorted.length * 0.5) - 1] / 1e6,
            sorted[(int) Math.ceil(sorted.length * 0.95) - 1] / 1e6, sorted[sorted.length - 1] / 1e6));
        var plan = jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, FROM, TO);
        Files.writeString(output.resolve(name + "-plan.txt"), String.join("\n", plan.stream().map(row -> row.values().iterator().next().toString()).toList()));
    }
}
