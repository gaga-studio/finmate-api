package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인사이트 — 지금 습관대로 가면 6개월 뒤 어디에 있는가.
 *
 * 이 화면의 설득력은 "미래를 맞힌다"가 아니라 "지금 이대로면"에서 나온다.
 * 그래서 예측 모델을 쓰지 않는다. <b>최근 실적을 그대로 연장</b>할 뿐이고,
 * 응답에 그 근거(몇 달치를 평균했는지)를 함께 실어 화면이 밝힐 수 있게 한다.
 *
 * 모델을 얹으면 숫자가 그럴듯해지지만 왜 그 숫자인지 아무도 설명할 수 없게 된다.
 * 금융 화면에서 설명할 수 없는 숫자는 쓰지 않느니만 못하다.
 */
@Service
public class ProjectionService {

	private static final int HORIZON_MONTHS = 6;
	/** 몇 달치 실적을 평균할지. 한 달만 보면 그달의 특이한 일이 6개월을 통째로 흔든다. */
	private static final int BASIS_MONTHS = 3;

	private final JdbcTemplate jdbc;

	public ProjectionService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Point(LocalDate month, long netWorth, boolean actual) {
	}

	public record Projection(
		List<Point> points,
		long monthlyNetFlow,
		int basisMonths,
		/** 화면이 그대로 보여줄 근거 문장. 숫자만 던지지 않는다. */
		String basis) {
	}

	/**
	 * 실적 구간과 투영 구간을 한 줄로 잇는다.
	 *
	 * 그래프가 과거와 미래를 한 선으로 그리므로, 어디까지가 실측이고 어디부터가 가정인지
	 * 점마다 표시해서 내려보낸다. 화면이 그 경계를 다르게 그릴 수 있어야 한다.
	 */
	@Transactional(readOnly = true)
	public Projection of(UUID personaId, LocalDate referenceMonth) {
		LocalDate last = referenceMonth.withDayOfMonth(1);
		LocalDate from = last.minusMonths(BASIS_MONTHS - 1L);

		List<Point> points = new ArrayList<>();
		// 실적 — 매달의 순증(소득 - 소비)을 쌓아 순자산 곡선을 만든다.
		// 시작 잔액은 데이터에 없으므로 0에서 시작하고, 화면은 "얼마나 늘었는가"를 본다.
		//
		// queryForList는 날짜를 java.sql.Date로 준다. 캐스팅하면 런타임에 터지므로
		// RowMapper에서 타입을 지정해 꺼낸다.
		record MonthNet(LocalDate month, long net) {
		}
		List<MonthNet> actuals = jdbc.query("""
			SELECT month, earned - spend AS net
			FROM persona_month WHERE persona_id = ? AND month <= ?
			ORDER BY month
			""",
			(rs, i) -> new MonthNet(rs.getObject("month", LocalDate.class), rs.getLong("net")),
			personaId, last);

		long cumulative = 0;
		for (MonthNet m : actuals) {
			cumulative += m.net();
			points.add(new Point(m.month(), cumulative, true));
		}

		long monthlyNetFlow = averageNetFlow(personaId, from, last);
		for (int i = 1; i <= HORIZON_MONTHS; i++) {
			cumulative += monthlyNetFlow;
			points.add(new Point(last.plusMonths(i), cumulative, false));
		}

		String basis = "최근 %d개월 평균(월 %,d원)을 그대로 이어 붙인 값입니다. 예측이 아닙니다."
			.formatted(BASIS_MONTHS, monthlyNetFlow);
		return new Projection(points, monthlyNetFlow, BASIS_MONTHS, basis);
	}

	/**
	 * 한 달에 얼마나 남는가.
	 *
	 * 저축·투자는 나가는 돈이지만 사라지는 게 아니라 옮겨지는 것이라 순증에서 빼지 않는다.
	 * 소비만 빠진다. 이걸 헷갈리면 열심히 저축할수록 자산이 줄어드는 그래프가 나온다.
	 */
	private long averageNetFlow(UUID personaId, LocalDate from, LocalDate to) {
		Long avg = jdbc.queryForObject("""
			SELECT round(avg(earned - spend)) FROM persona_month
			WHERE persona_id = ? AND month BETWEEN ? AND ?
			""", Long.class, personaId, from, to);
		return avg == null ? 0 : avg;
	}

	/**
	 * 지금 사면 목표가 며칠 늦어지는가.
	 *
	 * 발표자료의 "산다 vs 참는다" 시뮬레이션이다. 한 달에 남는 돈으로 나누면
	 * 그 물건 값을 메우는 데 며칠이 더 드는지가 나온다.
	 */
	@Transactional(readOnly = true)
	public int delayDays(UUID personaId, LocalDate referenceMonth, long price) {
		LocalDate last = referenceMonth.withDayOfMonth(1);
		long monthly = averageNetFlow(personaId, last.minusMonths(BASIS_MONTHS - 1L), last);
		if (monthly <= 0) {
			// 매달 마이너스면 "며칠 늦어진다"가 성립하지 않는다. 화면이 다르게 말해야 한다.
			return -1;
		}
		return (int) Math.ceil(price / (monthly / 30.0));
	}
}
