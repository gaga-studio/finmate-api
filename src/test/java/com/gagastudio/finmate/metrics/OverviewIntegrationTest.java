package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * 원장에서 만들어 낸 값이 원장과 맞는지 본다.
 *
 * 집계는 틀려도 그럴듯한 숫자가 나오기 때문에 "값이 있다"로는 부족하다.
 * 같은 구간을 다른 방식으로 다시 세어 맞춰 본다.
 */
@SpringBootTest
class OverviewIntegrationTest extends PostgresIntegrationTest {

	private static final int SAMPLE = 20;

	@Autowired
	private LedgerImporter importer;

	@Autowired
	private OverviewService overviews;

	@Autowired
	private JdbcTemplate jdbc;

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@BeforeEach
	void 적재한다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다. pipeline/05_generate.py를 먼저 실행하세요")
			.isTrue();
		jdbc.execute("TRUNCATE ledger_entry, persona CASCADE");
		importer.importFrom(bundlesDir(), SAMPLE);
	}

	private UUID anyPersona() {
		return jdbc.queryForObject("SELECT id FROM persona ORDER BY external_id LIMIT 1", UUID.class);
	}

	@Test
	void 기준일은_그_사람의_마지막_거래일이다() {
		UUID persona = anyPersona();
		var overview = overviews.of(persona, PeriodType.DAILY);

		var lastTxDate = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?",
			java.time.LocalDate.class, persona);
		assertThat(overview.referenceDate()).isEqualTo(lastTxDate);
	}

	@Test
	void 일간_지출이_그날_거래_합계와_같다() {
		UUID persona = anyPersona();
		var overview = overviews.of(persona, PeriodType.DAILY);

		Long spentAgain = jdbc.queryForObject("""
			SELECT COALESCE(-sum(amount), 0) FROM ledger_entry
			WHERE persona_id = ? AND occurred_on = ? AND flow = '소비'
			""", Long.class, persona, overview.referenceDate());

		assertThat(overview.budget().spent()).isEqualTo(spentAgain);
	}

	@Test
	void 기간이_넓어지면_지출이_줄지_않는다() {
		UUID persona = anyPersona();
		long daily = overviews.of(persona, PeriodType.DAILY).budget().spent();
		long weekly = overviews.of(persona, PeriodType.WEEKLY).budget().spent();
		long monthly = overviews.of(persona, PeriodType.MONTHLY).budget().spent();

		// 셋 다 같은 날 끝나고 시작만 당겨진다. 주간이 일간보다 작으면 경계 계산이 틀린 것이다.
		assertThat(weekly).isGreaterThanOrEqualTo(daily);
		assertThat(monthly).isGreaterThanOrEqualTo(weekly);
	}

	@Test
	void 주간은_일요일에_시작하고_기준일에_끝난다() {
		UUID persona = anyPersona();
		var overview = overviews.of(persona, PeriodType.WEEKLY);

		assertThat(overview.start().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);
		assertThat(overview.end()).isEqualTo(overview.referenceDate());
		assertThat(overview.start()).isBeforeOrEqualTo(overview.end());
	}

	@Test
	void 예산_한도는_그_사람의_목표_지출에서_나온다() {
		UUID persona = anyPersona();
		Long target = jdbc.queryForObject(
			"SELECT target_monthly_spend FROM persona WHERE id = ?", Long.class, persona);

		assertThat(overviews.of(persona, PeriodType.MONTHLY).budget().limit()).isEqualTo(target);
		assertThat(overviews.of(persona, PeriodType.WEEKLY).budget().limit())
			.isEqualTo(Math.round(target / 4.0));
		assertThat(overviews.of(persona, PeriodType.DAILY).budget().limit())
			.isEqualTo(Math.round(target / 30.0));
	}

	@Test
	void 소비_탑5는_소비만_금액_큰_순으로_준다() {
		UUID persona = anyPersona();
		List<OverviewService.Spend> top = overviews.of(persona, PeriodType.MONTHLY).topSpends();

		assertThat(top).isNotEmpty().hasSizeLessThanOrEqualTo(5);
		assertThat(top).allSatisfy(s -> {
			assertThat(s.amount()).isPositive();
			// 저축·투자는 소비가 아니다 — 섞이면 "이번 달 가장 많이 쓴 곳"이 청약통장이 된다
			assertThat(s.category()).isNotIn("saving", "invest", "income");
		});
		assertThat(top).isSortedAccordingTo((a, b) -> Long.compare(b.amount(), a.amount()));
	}

	@Test
	void 남은_예산은_음수가_되지_않는다() {
		// 한도를 넘겨도 물잔은 비어 있을 뿐 아래로 내려가지 않는다
		for (UUID persona : jdbc.queryForList("SELECT id FROM persona", UUID.class)) {
			var budget = overviews.of(persona, PeriodType.MONTHLY).budget();
			assertThat(budget.remaining()).isNotNegative();
			assertThat(budget.pct()).isBetween(0.0, 1.0);
		}
	}
}
