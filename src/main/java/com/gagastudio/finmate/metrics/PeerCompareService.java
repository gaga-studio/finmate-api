package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 같은 달의 자료가 완전히 적재된 동일 소득대(본인 포함)를 비교한다.
 * 무거래자는 0원이며, 미적재·일부 기간만 적재된 사람은 모집단에서 제외한다.
 * persona.data_from/data_to는 프로필과 원장을 한 트랜잭션으로 적재한 자료 범위다.
 */
@Service
public class PeerCompareService {

	private final JdbcTemplate jdbc;

	public PeerCompareService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Group(String label, int members, long avgSpend, long avgSaved) {
	}

	public record Comparison(
		LocalDate month,
		String myBand,
		Long mySpend,
		Long peerAvgSpend,
		/** 또래 중 내가 적게 쓴 비율 0~100. 높을수록 아껴 쓴 것이다. */
		Integer percentile,
		int peerCount,
		List<Group> bands, String dataStatus, String source) {
	}

	/** 소득대별 평균. 피드 상단 "그룹 보기"가 이걸 쓴다. */
	@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
	public List<Group> byIncomeBand(LocalDate month) {
		return jdbc.query("""
			SELECT p.income_band AS label,
			       count(*) AS members,
			       COALESCE(round(avg(COALESCE(m.spend, 0))), 0) AS avg_spend,
			       COALESCE(round(avg(COALESCE(m.saved, 0))), 0) AS avg_saved
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.data_from <= ? AND p.data_to >= ?
			GROUP BY p.income_band
			ORDER BY p.income_band
			""",
			(rs, i) -> new Group(rs.getString("label"), rs.getInt("members"),
				rs.getLong("avg_spend"), rs.getLong("avg_saved")),
			month.withDayOfMonth(1), month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1).minusDays(1));
	}

	/**
	 * 나를 또래 안에 세운다.
	 *
	 * 비교 대상은 같은 소득대다. 소득이 다르면 지출이 다른 게 당연해서, 그걸 나란히 놓으면
	 * 화면이 "너는 많이 쓴다"고 잘못 말한다.
	 */
	@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
	public Comparison forPersona(UUID personaId, LocalDate month) {
		LocalDate first = month.withDayOfMonth(1);

		// 자료 범위를 먼저 검사한다. 집계 행이 없다는 사실만으로 무거래를 판단하지 않는다.
		var row = jdbc.queryForMap("""
			SELECT p.income_band, p.data_from, p.data_to, COALESCE(m.spend, 0) AS my_spend
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.id = ?
			""", first, personaId);

		String band = (String) row.get("income_band");
		LocalDate last = first.plusMonths(1).minusDays(1);
		LocalDate dataFrom = ((java.sql.Date) row.get("data_from")).toLocalDate();
		LocalDate dataTo = ((java.sql.Date) row.get("data_to")).toLocalDate();
		if (dataFrom.isAfter(first) || dataTo.isBefore(last)) {
			return new Comparison(first, band, null, null, null, 0, List.of(), "NO_DATA", "SYNTHETIC");
		}
		long mySpend = ((Number) row.get("my_spend")).longValue();

		var peers = jdbc.queryForMap("""
			SELECT count(*) AS peer_count,
			       COALESCE(round(avg(COALESCE(m.spend, 0))), 0) AS avg_spend,
			       count(*) FILTER (WHERE COALESCE(m.spend, 0) > ?) AS spent_more
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.income_band = ? AND p.data_from <= ? AND p.data_to >= ?
			""", mySpend, first, band, first, last);

		int peerCount = ((Number) peers.get("peer_count")).intValue();
		long spentMore = ((Number) peers.get("spent_more")).longValue();
		int percentile = peerCount == 0 ? 0 : (int) Math.round(spentMore * 100.0 / peerCount);

		return new Comparison(first, band, mySpend,
			((Number) peers.get("avg_spend")).longValue(), percentile, peerCount, byIncomeBand(first), "AVAILABLE", "SYNTHETIC");
	}
}
