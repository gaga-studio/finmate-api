package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이 탭 한 화면에 필요한 값을 원장에서 만들어 낸다.
 *
 * 앱은 지표를 저장하지 않는다. 예산도 저축 진행률도 소비 탑5도 전부 거래를 다시 세어 만든다
 * (발표자료 13쪽: "매 렌더 원장 재계산"). 그 구조를 그대로 서버로 옮겼다 —
 * 먼저 순진하게 만들고, 얼마나 느린지 재고, 그 다음에 고친다.
 * 재기 전에 고치면 무엇이 좋아졌는지 말할 수 없다.
 *
 * 화면 하나를 여러 번 왕복하지 않도록 한 번에 돌려준다. 측정 단위도 그래야 화면과 맞는다.
 */
@Service
public class OverviewService {

	private final JdbcTemplate jdbc;

	public OverviewService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Budget(long limit, long spent, long remaining, double pct) {
	}

	public record Spend(String category, String merchant, long amount, LocalDate date) {
	}

	public record Overview(
		String personaId,
		LocalDate referenceDate,
		String period,
		LocalDate start,
		LocalDate end,
		Budget budget,
		Long saved,
		Long invested,
		Long earned,
		List<Spend> topSpends,
		String dataStatus, String source, LocalDate dataFrom, LocalDate dataTo) {
	}

	@Transactional(readOnly = true)
	public Overview of(UUID personaId, PeriodType period) {
		return of(personaId, period, null);
	}

	@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
	public Overview of(UUID personaId, PeriodType period, LocalDate date) {
		LocalDate reference = date == null ? referenceDate(personaId) : date;
		PeriodType.Range range = period.range(reference);
		var coverage = jdbc.queryForObject("SELECT data_from, data_to FROM persona WHERE id = ?",
			(rs, i) -> new PeriodType.Range(rs.getObject("data_from", LocalDate.class),
				rs.getObject("data_to", LocalDate.class)), personaId);
		if (range.start().isBefore(coverage.start()) || range.end().isAfter(coverage.end())) {
			return new Overview(personaId.toString(), reference, period.wireName(), range.start(), range.end(),
				null, null, null, null, List.of(), "NO_DATA", "SYNTHETIC", coverage.start(), coverage.end());
		}
		long targetMonthlySpend = targetMonthlySpend(personaId);

		long limit = period.budgetLimit(targetMonthlySpend);
		Flows flows = flows(personaId, range);
		long remaining = Math.max(0, limit - flows.spent());

		return new Overview(
			personaId.toString(), reference, period.wireName(), range.start(), range.end(),
			new Budget(limit, flows.spent(), remaining, limit == 0 ? 0 : (double) remaining / limit),
			flows.saved(), flows.invested(), flows.earned(),
			topSpends(personaId, range, 5), "AVAILABLE", "SYNTHETIC", coverage.start(), coverage.end());
	}

	/**
	 * "오늘"을 벽시계에서 읽지 않는다.
	 *
	 * 이 데이터는 2026-01~07 구간의 합성 원장이라, 실제 오늘로 잡으면 화면이 전부 빈다.
	 * 그 사람의 마지막 거래일을 기준일로 쓴다 — 앱도 같은 이유로 DEMO_TODAY를 고정해 두었다.
	 */
	private LocalDate referenceDate(UUID personaId) {
		LocalDate last = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?", LocalDate.class, personaId);
		if (last == null) {
			// 적재 기간의 무거래자도 0원 요약을 볼 수 있다.
			return jdbc.queryForObject("SELECT data_to FROM persona WHERE id = ?", LocalDate.class, personaId);
		}
		return last;
	}

	private long targetMonthlySpend(UUID personaId) {
		Long v = jdbc.queryForObject(
			"SELECT target_monthly_spend FROM persona WHERE id = ?", Long.class, personaId);
		if (v == null) {
			throw new IllegalStateException("없는 사용자입니다: " + personaId);
		}
		return v;
	}

	private record Flows(long spent, long saved, long invested, long earned) {
	}

	/**
	 * 소비·저축·투자·소득을 한 번에 센다.
	 *
	 * 넷을 따로 조회하면 같은 구간을 네 번 스캔한다. FILTER로 한 번에 접는다.
	 * 부호는 원장 그대로 두고(지출 음수) 여기서 양수로 뒤집는다 — 화면이 쓰는 건 "쓴 금액"이다.
	 */
	private Flows flows(UUID personaId, PeriodType.Range range) {
		return jdbc.queryForObject("""
			SELECT
			  COALESCE(-sum(amount) FILTER (WHERE flow = '소비'), 0) AS spent,
			  COALESCE(-sum(amount) FILTER (WHERE flow = '저축'), 0) AS saved,
			  COALESCE(-sum(amount) FILTER (WHERE flow = '투자'), 0) AS invested,
			  COALESCE( sum(amount) FILTER (WHERE flow = '소득'), 0) AS earned
			FROM ledger_entry
			WHERE persona_id = ? AND occurred_on BETWEEN ? AND ?
			""",
			(rs, i) -> new Flows(rs.getLong("spent"), rs.getLong("saved"),
				rs.getLong("invested"), rs.getLong("earned")),
			personaId, range.start(), range.end());
	}

	/**
	 * 소비 탑N. 저축·투자는 소비가 아니므로 뺀다.
	 *
	 * 정렬에서 한 번 틀렸다. `-amount AS amount`로 별칭을 주고 `ORDER BY amount`라고 썼더니
	 * Postgres가 테이블 컬럼이 아니라 출력 별칭(양수)을 잡아, 가장 적게 쓴 것부터 나왔다.
	 * 테이블을 e로 별칭 붙여 어느 쪽을 정렬하는지 못 헷갈리게 했다.
	 */
	private List<Spend> topSpends(UUID personaId, PeriodType.Range range, int n) {
		return jdbc.query("""
			SELECT e.category, e.merchant, -e.amount AS amount, e.occurred_on
			FROM ledger_entry e
			WHERE e.persona_id = ? AND e.occurred_on BETWEEN ? AND ? AND e.flow = '소비'
			ORDER BY e.amount ASC
			LIMIT ?
			""",
			(rs, i) -> new Spend(rs.getString("category"), rs.getString("merchant"),
				rs.getLong("amount"), rs.getObject("occurred_on", LocalDate.class)),
			personaId, range.start(), range.end(), n);
	}
}
