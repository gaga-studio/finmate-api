package com.gagastudio.finmate.ledger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * finmate-data 번들을 Postgres에 적재한다.
 *
 * 89만 행이라 JPA로 한 건씩 persist하면 영속성 컨텍스트가 그 전부를 들고 있게 된다.
 * 적재는 파일 한 줄을 한 행으로 옮기는 일이고 도메인 규칙이 없으므로 JDBC 배치로 바로 넣는다.
 *
 * 몇 번을 돌려도 결과가 같아야 한다. 유니크 제약에 `ON CONFLICT DO NOTHING`을 걸어
 * 중간에 끊고 다시 돌려도 중복이 쌓이지 않는다.
 */
@Component
public class LedgerImporter {

	private static final Logger log = LoggerFactory.getLogger(LedgerImporter.class);
	private static final int BATCH = 1_000;

	private static final String INSERT_PERSONA = """
		INSERT INTO persona (id, external_id, display_name, age, cohort, job, archetype, region,
		    household_type, household_size, monthly_income, income_band, income_regularity,
		    target_monthly_spend, target_saving_rate, target_investment_rate, invest_participation,
		    risk_score, risk_attitude, data_from, data_to)
		VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT (external_id) DO NOTHING
		""";

	private static final String INSERT_ENTRY = """
		INSERT INTO ledger_entry (persona_id, external_id, occurred_at, merchant, amount,
		    category, flow, major, minor, rule_id, payment_method, memo)
		VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
		ON CONFLICT (persona_id, external_id) DO NOTHING
		""";

	private final JdbcTemplate jdbc;

	public LedgerImporter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Result(int personas, int entries, long millis) {
	}

	/**
	 * @param bundlesDir finmate-data의 {@code outputs/finmate_v3/bundles}
	 * @param limit 적재할 인원 수. 0 이하면 전부.
	 */
	@Transactional
	public Result importFrom(Path bundlesDir, int limit) {
		List<Path> dirs = listBundles(bundlesDir, limit);
		if (dirs.isEmpty()) {
			throw new IllegalArgumentException("번들이 없습니다: " + bundlesDir
				+ " (finmate-data에서 python3 pipeline/05_generate.py를 먼저 실행하세요)");
		}

		int personas = 0;
		int entries = 0;
		long startedAt = System.nanoTime();

		for (Path dir : dirs) {
			BundleRows.Bundle bundle = BundleReader.read(dir);
			personas += insertPersona(bundle.persona());
			entries += insertEntries(bundle.entries());
		}

		long ms = (System.nanoTime() - startedAt) / 1_000_000;
		log.info("적재 완료 — persona {}명 · 거래 {}행 · {}ms", personas, entries, ms);
		return new Result(personas, entries, ms);
	}

	private List<Path> listBundles(Path bundlesDir, int limit) {
		if (!Files.isDirectory(bundlesDir)) {
			return List.of();
		}
		try (var stream = Files.list(bundlesDir)) {
			var sorted = stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName));
			return limit > 0 ? sorted.limit(limit).toList() : sorted.toList();
		} catch (IOException e) {
			throw new UncheckedIOException("번들 목록을 읽지 못했습니다: " + bundlesDir, e);
		}
	}

	private int insertPersona(BundleRows.PersonaRow p) {
		return jdbc.update(INSERT_PERSONA, ps -> {
			ps.setObject(1, p.id());
			ps.setString(2, p.externalId());
			ps.setString(3, p.displayName());
			ps.setShort(4, p.age());
			ps.setString(5, p.cohort());
			ps.setString(6, p.job());
			ps.setString(7, p.archetype());
			ps.setString(8, p.region());
			ps.setString(9, p.householdType());
			ps.setShort(10, p.householdSize());
			ps.setLong(11, p.monthlyIncome());
			ps.setString(12, p.incomeBand());
			ps.setString(13, p.incomeRegularity());
			ps.setLong(14, p.targetMonthlySpend());
			ps.setBigDecimal(15, p.targetSavingRate());
			ps.setBigDecimal(16, p.targetInvestmentRate());
			ps.setBoolean(17, p.investParticipation());
			ps.setShort(18, p.riskScore());
			ps.setString(19, p.riskAttitude());
			ps.setObject(20, p.dataFrom());
			ps.setObject(21, p.dataTo());
		});
	}

	private int insertEntries(List<BundleRows.LedgerRow> rows) {
		// JdbcTemplate이 BATCH 크기로 잘라 보낸다. 결과가 배치별 배열의 배열로 온다.
		int[][] counts = jdbc.batchUpdate(INSERT_ENTRY, rows, BATCH, LedgerImporter::bind);
		int written = 0;
		for (int[] batch : counts) {
			for (int c : batch) {
				// ON CONFLICT로 걸러진 행은 0, 드라이버가 개수를 모르면 음수를 준다
				written += Math.max(c, 0);
			}
		}
		return written;
	}

	private static void bind(PreparedStatement ps, BundleRows.LedgerRow e) throws SQLException {
		ps.setObject(1, e.personaId());
		ps.setString(2, e.externalId());
		ps.setTimestamp(3, Timestamp.valueOf(e.occurredAt()));
		ps.setString(4, e.merchant());
		ps.setLong(5, e.amount());
		ps.setString(6, e.category());
		ps.setString(7, e.flow());
		ps.setString(8, e.major());
		ps.setString(9, e.minor());
		setNullable(ps, 10, e.ruleId());
		setNullable(ps, 11, e.paymentMethod());
		setNullable(ps, 12, e.memo());
	}

	private static void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.VARCHAR);
		} else {
			ps.setString(index, value);
		}
	}
}
