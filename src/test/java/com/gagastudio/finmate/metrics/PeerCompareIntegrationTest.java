package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 사전 집계를 믿어도 되는지 지킨다.
 *
 * 사전 집계는 원장을 복사해 둔 것이라 언제든 어긋날 수 있다. 그래서 "빠른가"가 아니라
 * "원장과 같은가"를 본다. 이게 깨지면 화면의 모든 숫자가 조용히 틀린다.
 */
@SpringBootTest
class PeerCompareIntegrationTest extends PostgresIntegrationTest {

	private static final int SAMPLE = 40;

	@Autowired private LedgerImporter importer;
	@Autowired private MonthlyRollup rollup;
	@Autowired private PeerCompareService peers;
	@Autowired private JdbcTemplate jdbc;

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@BeforeEach
	void 적재하고_집계한다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다. pipeline/05_generate.py를 먼저 실행하세요")
			.isTrue();
		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month CASCADE");
		importer.importFrom(bundlesDir(), SAMPLE);
		rollup.rebuildAll();
	}

	@Test
	void 사전_집계가_원장을_직접_센_값과_같다() {
		List<java.util.Map<String, Object>> mismatched = jdbc.queryForList("""
			SELECT m.persona_id, m.month, m.spend, live.spend AS live_spend
			FROM persona_month m
			JOIN LATERAL (
			  SELECT COALESCE(-sum(amount) FILTER (WHERE flow = '소비'), 0) AS spend
			  FROM ledger_entry e
			  WHERE e.persona_id = m.persona_id
			    AND date_trunc('month', e.occurred_on)::date = m.month
			) live ON TRUE
			WHERE m.spend <> live.spend
			""");
		assertThat(mismatched).isEmpty();
	}

	@Test
	void 원장에_있는_모든_사람달이_집계에도_있다() {
		// 집계가 원장보다 적으면 화면이 조용히 0을 보여준다
		Long missing = jdbc.queryForObject("""
			SELECT count(*) FROM (
			  SELECT DISTINCT persona_id, date_trunc('month', occurred_on)::date AS month
			  FROM ledger_entry
			) src
			WHERE NOT EXISTS (
			  SELECT 1 FROM persona_month m
			  WHERE m.persona_id = src.persona_id AND m.month = src.month
			)
			""", Long.class);
		assertThat(missing).isZero();
	}

	@Test
	void 다시_돌려도_같은_값이_나온다() {
		Long before = jdbc.queryForObject("SELECT sum(spend) FROM persona_month", Long.class);
		rollup.rebuildAll();
		Long after = jdbc.queryForObject("SELECT sum(spend) FROM persona_month", Long.class);
		Long rows = jdbc.queryForObject("SELECT count(*) FROM persona_month", Long.class);

		assertThat(after).isEqualTo(before);
		// UPSERT라 두 번 돌려도 행이 늘지 않는다
		assertThat(rows).isEqualTo(jdbc.queryForObject("""
			SELECT count(*) FROM (
			  SELECT DISTINCT persona_id, date_trunc('month', occurred_on)::date FROM ledger_entry
			) t""", Long.class));
	}

	@Test
	void 한_사람만_다시_세도_그_사람_값이_맞는다() {
		UUID persona = jdbc.queryForObject(
			"SELECT id FROM persona ORDER BY external_id LIMIT 1", UUID.class);

		// 집계만 망가뜨린다. 원장은 그대로다.
		jdbc.update("UPDATE persona_month SET spend = 999999999 WHERE persona_id = ?", persona);
		rollup.rebuildPersona(persona);

		Long wrong = jdbc.queryForObject(
			"SELECT count(*) FROM persona_month WHERE persona_id = ? AND spend = 999999999",
			Long.class, persona);
		assertThat(wrong).isZero();
	}

	@Test
	void 소득대별_평균에_모든_사람이_들어간다() {
		LocalDate month = jdbc.queryForObject(
			"SELECT max(month) FROM persona_month", LocalDate.class);
		List<PeerCompareService.Group> groups = peers.byIncomeBand(month);

		int total = groups.stream().mapToInt(PeerCompareService.Group::members).sum();
		assertThat(total).isEqualTo(SAMPLE);
		assertThat(groups).allSatisfy(g -> assertThat(g.avgSpend()).isNotNegative());
	}

	@Test
	void 또래_안에서의_내_위치가_0에서_100_사이다() {
		LocalDate month = jdbc.queryForObject("SELECT max(month) FROM persona_month", LocalDate.class);
		for (UUID persona : jdbc.queryForList("SELECT id FROM persona", UUID.class)) {
			var c = peers.forPersona(persona, month);
			assertThat(c.percentile()).isBetween(0, 100);
			assertThat(c.peerCount()).isPositive();
			// 비교 대상은 같은 소득대다 — 소득이 다르면 지출이 다른 게 당연하다
			assertThat(c.myBand()).isNotBlank();
		}
	}
}
