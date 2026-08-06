package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 — 나와 비슷한 또래를 찾고 그들의 금융 행동을 본다.
 *
 * 발표자료가 핵심 해법으로 든 화면이다("나만 안 하고 있었다는 걸, 문득 깨닫는 순간").
 *
 * <b>남의 금융 정보를 보여주는 화면이다.</b> 그래서 두 가지를 지킨다 —
 * 금액을 그대로 내보내지 않고 구간으로 접고, 인원이 너무 적은 그룹은 아예 만들지 않는다.
 * 세 명짜리 그룹의 평균은 사실상 개인 정보다.
 */
@Service
public class FeedService {

	/** 이보다 적은 그룹은 평균이 개인을 드러낸다. finmate-data의 k-익명성 리포트와 같은 기준. */
	private static final int MIN_GROUP_SIZE = 20;

	private final JdbcTemplate jdbc;

	public FeedService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Group(String id, String label, String description, int members) {
	}

	public record Mate(
		String nickname, int age, String job, String region,
		/** 예산을 얼마나 남겼는지 %. 금액이 아니다. */
		int budgetLeftPct,
		/** 소비 구간 — "월 70만원대" */
		String spendBand,
		/** 상위 소비 카테고리 셋. 금액 없이 이름만. */
		List<String> topCategories) {
	}

	/**
	 * 나와 비슷한 그룹들.
	 *
	 * 앱의 목록(소득 유사·소비 유사·지역 또래)을 그대로 만든다. 인원은 실제로 세어 넣는다 —
	 * 화면에 1,570명이라 적혀 있는데 실제로 안 그러면 그 숫자가 거짓말이 된다.
	 */
	@Transactional(readOnly = true)
	public List<Group> groupsFor(UUID personaId, LocalDate month) {
		var me = jdbc.queryForMap("""
			SELECT p.income_band, p.age, p.region, p.cohort, COALESCE(m.spend, 0) AS spend
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.id = ?
			""", month.withDayOfMonth(1), personaId);

		String band = (String) me.get("income_band");
		long spend = ((Number) me.get("spend")).longValue();
		int age = ((Number) me.get("age")).intValue();
		String region = ((String) me.get("region"));
		// "대전 서구" → "대전". 구 단위로 자르면 그룹이 몇 명 안 된다.
		String city = region.split(" ")[0];

		return java.util.stream.Stream.of(
			group("g-income", "소득 유사", "월 소득대 " + band, countByBand(band)),
			group("g-spend", "소비 유사", spendBandLabel(spend),
				countBySpendBand(month, spend)),
			group("g-region", city + " 또래", "같은 지역 · ±2세",
				countByRegionAge(city, age)))
			.filter(g -> g.members() >= MIN_GROUP_SIZE)
			.toList();
	}

	/**
	 * 그룹 안의 사람들. 금액은 구간으로만 나간다.
	 *
	 * 나 자신은 뺀다 — 또래를 구경하는 화면에 내가 섞이면 비교가 무의미하다.
	 */
	@Transactional(readOnly = true)
	public List<Mate> matesInBand(UUID personaId, LocalDate month, int limit) {
		LocalDate first = month.withDayOfMonth(1);
		return jdbc.query("""
			SELECT p.id, p.display_name, p.age, p.job, p.region,
			       p.target_monthly_spend, COALESCE(m.spend, 0) AS spend
			FROM persona p
			JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.income_band = (SELECT income_band FROM persona WHERE id = ?)
			  AND p.id <> ?
			ORDER BY p.external_id
			LIMIT ?
			""",
			(rs, i) -> {
				UUID id = rs.getObject("id", UUID.class);
				long target = rs.getLong("target_monthly_spend");
				long spent = rs.getLong("spend");
				int leftPct = target == 0 ? 0
					: (int) Math.max(0, Math.round((target - spent) * 100.0 / target));
				return new Mate(
					rs.getString("display_name"), rs.getInt("age"), rs.getString("job"),
					rs.getString("region").split(" ")[0],
					leftPct, spendBandLabel(spent), topCategories(id, first));
			},
			first, personaId, personaId, limit);
	}

	/** 카테고리 이름만. 금액을 붙이면 남의 지출액을 그대로 보여주는 것이 된다. */
	private List<String> topCategories(UUID personaId, LocalDate month) {
		return jdbc.queryForList("""
			SELECT category FROM ledger_entry
			WHERE persona_id = ? AND flow = '소비'
			  AND occurred_on >= ? AND occurred_on < (? + interval '1 month')
			GROUP BY category ORDER BY -sum(amount) DESC LIMIT 3
			""", String.class, personaId, month, month);
	}

	/** 금액을 10만원 구간으로 접는다. "월 70만원대"까지만 말한다. */
	static String spendBandLabel(long spend) {
		if (spend <= 0) {
			return "기록 없음";
		}
		long band = spend / 100_000 * 10;
		return "월 %d만원대".formatted(band);
	}

	private Group group(String id, String label, String desc, int members) {
		return new Group(id, label, desc, members);
	}

	private int countByBand(String band) {
		Integer n = jdbc.queryForObject(
			"SELECT count(*) FROM persona WHERE income_band = ?", Integer.class, band);
		return n == null ? 0 : n;
	}

	private int countBySpendBand(LocalDate month, long spend) {
		long low = spend / 100_000 * 100_000;
		Integer n = jdbc.queryForObject("""
			SELECT count(*) FROM persona_month WHERE month = ? AND spend >= ? AND spend < ?
			""", Integer.class, month.withDayOfMonth(1), low, low + 100_000);
		return n == null ? 0 : n;
	}

	private int countByRegionAge(String city, int age) {
		Integer n = jdbc.queryForObject("""
			SELECT count(*) FROM persona WHERE region LIKE ? AND age BETWEEN ? AND ?
			""", Integer.class, city + "%", age - 2, age + 2);
		return n == null ? 0 : n;
	}
}
