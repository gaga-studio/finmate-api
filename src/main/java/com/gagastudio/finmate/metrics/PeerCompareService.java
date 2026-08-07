package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 또래 비교 — "나와 비슷한 사람들은 얼마나 쓰는가".
 *
 * 발표자료가 핵심 해법으로 든 화면이다("타인 금융 구경 → 동기자극").
 * 사전 집계({@code persona_month})를 읽는다. 원장을 직접 세면 한 달치가 10만 행이고,
 * 접히고 나면 2,000행이라 미리 접어 두는 쪽이 17.7배 빠르다 (docs/PERF_RESULT.md).
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
		long mySpend,
		long peerAvgSpend,
		/** 또래 중 내가 적게 쓴 비율 0~100. 높을수록 아껴 쓴 것이다. */
		int percentile,
		int peerCount,
		List<Group> bands) {
	}

	/** 소득대별 평균. 피드 상단 "그룹 보기"가 이걸 쓴다. */
	@Transactional(readOnly = true)
	public List<Group> byIncomeBand(LocalDate month) {
		return jdbc.query("""
			SELECT p.income_band AS label,
			       count(*) AS members,
			       COALESCE(round(avg(m.spend)), 0) AS avg_spend,
			       COALESCE(round(avg(m.saved)), 0) AS avg_saved
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			GROUP BY p.income_band
			ORDER BY p.income_band
			""",
			(rs, i) -> new Group(rs.getString("label"), rs.getInt("members"),
				rs.getLong("avg_spend"), rs.getLong("avg_saved")),
			month.withDayOfMonth(1));
	}

	/**
	 * 나를 또래 안에 세운다.
	 *
	 * 비교 대상은 같은 소득대다. 소득이 다르면 지출이 다른 게 당연해서, 그걸 나란히 놓으면
	 * 화면이 "너는 많이 쓴다"고 잘못 말한다.
	 */
	@Transactional(readOnly = true)
	public Comparison forPersona(UUID personaId, LocalDate month) {
		LocalDate first = month.withDayOfMonth(1);

		// 집계가 없는 달이면 0으로 본다 — 그달에 거래가 없었다는 뜻이다
		var row = jdbc.queryForMap("""
			SELECT p.income_band, COALESCE(m.spend, 0) AS my_spend
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.id = ?
			""", first, personaId);

		String band = (String) row.get("income_band");
		long mySpend = ((Number) row.get("my_spend")).longValue();

		var peers = jdbc.queryForMap("""
			SELECT count(*) AS peer_count,
			       COALESCE(round(avg(m.spend)), 0) AS avg_spend,
			       count(*) FILTER (WHERE COALESCE(m.spend, 0) > ?) AS spent_more
			FROM persona p
			LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = ?
			WHERE p.income_band = ?
			""", mySpend, first, band);

		int peerCount = ((Number) peers.get("peer_count")).intValue();
		long spentMore = ((Number) peers.get("spent_more")).longValue();
		int percentile = peerCount == 0 ? 0 : (int) Math.round(spentMore * 100.0 / peerCount);

		return new Comparison(first, band, mySpend,
			((Number) peers.get("avg_spend")).longValue(), percentile, peerCount, byIncomeBand(first));
	}
}
