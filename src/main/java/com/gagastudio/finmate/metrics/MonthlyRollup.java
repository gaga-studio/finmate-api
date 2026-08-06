package com.gagastudio.finmate.metrics;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사람×월 집계를 원장에서 다시 만든다.
 *
 * 사전 집계는 공짜가 아니다 — 원장이 바뀌면 여기도 틀어진다. 그래서 두 가지를 지킨다.
 *
 * 하나, 원장이 유일한 진실이다. 이 테이블은 언제든 원장에서 통째로 다시 만들 수 있고,
 * 그렇게 만든 값이 직접 집계한 값과 같은지는 테스트가 지킨다.
 *
 * 둘, 갱신 단위를 사람으로 잡았다. 거래 한 건이 들어오면 그 사람의 그 달만 다시 세면 된다.
 * 지금은 적재가 배치 한 번이라 전체 재생성만 쓰지만, 거래 추가 API가 생기면
 * {@link #rebuildPersona(UUID)}가 그 자리를 맡는다.
 */
@Component
public class MonthlyRollup {

	private static final Logger log = LoggerFactory.getLogger(MonthlyRollup.class);

	private static final String AGGREGATE = """
		SELECT persona_id, date_trunc('month', occurred_on)::date AS month,
		       COALESCE(-sum(amount) FILTER (WHERE flow = '소비'), 0) AS spend,
		       COALESCE(-sum(amount) FILTER (WHERE flow = '저축'), 0) AS saved,
		       COALESCE(-sum(amount) FILTER (WHERE flow = '투자'), 0) AS invested,
		       COALESCE( sum(amount) FILTER (WHERE flow = '소득'), 0) AS earned
		FROM ledger_entry
		""";

	private static final String UPSERT_TAIL = """
		ON CONFLICT (persona_id, month) DO UPDATE SET
		  spend = EXCLUDED.spend, saved = EXCLUDED.saved,
		  invested = EXCLUDED.invested, earned = EXCLUDED.earned
		""";

	private final JdbcTemplate jdbc;

	public MonthlyRollup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 전체 재생성. 적재 직후에 한 번 돌린다. */
	@Transactional
	public int rebuildAll() {
		long t0 = System.nanoTime();
		int rows = jdbc.update("""
			INSERT INTO persona_month (persona_id, month, spend, saved, invested, earned)
			""" + AGGREGATE + " GROUP BY 1, 2 " + UPSERT_TAIL);
		log.info("사전 집계 재생성 — {}행 · {}ms", rows, (System.nanoTime() - t0) / 1_000_000);
		return rows;
	}

	/** 한 사람만 다시 센다. 거래가 바뀐 사람만 건드리면 되는 자리다. */
	@Transactional
	public int rebuildPersona(UUID personaId) {
		// 거래가 사라진 달은 집계에도 남으면 안 된다
		jdbc.update("DELETE FROM persona_month WHERE persona_id = ?", personaId);
		return jdbc.update("""
			INSERT INTO persona_month (persona_id, month, spend, saved, invested, earned)
			""" + AGGREGATE + " WHERE persona_id = ? GROUP BY 1, 2 " + UPSERT_TAIL, personaId);
	}

	/** 그 달의 집계가 있는지. 없으면 화면이 0으로 보이므로 호출부가 알아야 한다. */
	public boolean hasMonth(LocalDate month) {
		Long n = jdbc.queryForObject(
			"SELECT count(*) FROM persona_month WHERE month = ?", Long.class, month.withDayOfMonth(1));
		return n != null && n > 0;
	}
}
