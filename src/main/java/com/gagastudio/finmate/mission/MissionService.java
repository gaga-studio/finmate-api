package com.gagastudio.finmate.mission;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gagastudio.finmate.metrics.PeriodType;

/**
 * 미션 — 오늘 할 수 있는 작은 행동.
 *
 * 발표자료의 두 번째 해법이다("무엇부터 해야 할지 몰라서 못 한다" → 미션으로 실행 장벽 해소).
 *
 * <b>진행률을 저장하지 않는다.</b> 예산 챌린지의 성패는 그날 얼마 썼는지가 정하므로,
 * 원장에서 매번 판정한다. 저장하면 원장과 어긋날 수 있고, 어긋난 쪽이 화면에 뜬다.
 * 저장하는 것은 "무엇을 담았는가"와 "보상을 줬는가"뿐이다.
 */
@Service
public class MissionService {

	private final JdbcTemplate jdbc;

	public MissionService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Progress(
		String missionId, String title, String kind, int reward, String reason,
		/** 0~100. 원장에서 매번 센 값이다. */
		int percent,
		boolean achieved) {
	}

	public record DayMark(LocalDate date, long spent, long limit, boolean kept) {
	}

	/** 담을 수 있는 미션. 그 사람의 씀씀이를 보고 이유를 붙인다. */
	@Transactional(readOnly = true)
	public List<Progress> recommended(UUID personaId, LocalDate referenceDate) {
		return jdbc.query("""
			SELECT m.id, m.title, m.kind, m.reward, m.reason
			FROM mission m
			WHERE NOT EXISTS (
			  SELECT 1 FROM persona_mission pm
			  WHERE pm.persona_id = ? AND pm.mission_id = m.id
			)
			ORDER BY m.id
			""",
			(rs, i) -> new Progress(rs.getString("id"), rs.getString("title"), rs.getString("kind"),
				rs.getInt("reward"), rs.getString("reason"), 0, false),
			personaId);
	}

	/** 담은 미션의 현재 상태. 전부 원장에서 다시 센다. */
	@Transactional(readOnly = true)
	public List<Progress> inProgress(UUID personaId, LocalDate referenceDate) {
		return jdbc.query("""
			SELECT m.id, m.title, m.kind, m.reward, m.reason, pm.accepted_on
			FROM persona_mission pm JOIN mission m ON m.id = pm.mission_id
			WHERE pm.persona_id = ?
			ORDER BY pm.accepted_on
			""",
			(rs, i) -> {
				String kind = rs.getString("kind");
				LocalDate from = rs.getObject("accepted_on", LocalDate.class);
				int percent = judge(personaId, kind, from, referenceDate);
				return new Progress(rs.getString("id"), rs.getString("title"), kind,
					rs.getInt("reward"), rs.getString("reason"), percent, percent >= 100);
			},
			personaId);
	}

	@Transactional
	public boolean accept(UUID personaId, String missionId, LocalDate today) {
		// 담기 전의 지출로 보상을 주지 않으려고 담은 날을 기록한다
		return jdbc.update("""
			INSERT INTO persona_mission (persona_id, mission_id, accepted_on) VALUES (?, ?, ?)
			ON CONFLICT (persona_id, mission_id) DO NOTHING
			""", personaId, missionId, today) > 0;
	}

	/**
	 * 달성한 미션에 보상을 준다.
	 *
	 * 같은 미션의 같은 날 판정으로 두 번 주지 않는다. 화면이 새로고침될 때마다 포인트가
	 * 불어나면 안 되고, 그건 사용자가 아니라 우리가 막아야 한다.
	 */
	@Transactional
	public int settle(UUID personaId, LocalDate referenceDate) {
		int granted = 0;
		for (Progress p : inProgress(personaId, referenceDate)) {
			if (!p.achieved()) {
				continue;
			}
			granted += jdbc.update("""
				INSERT INTO point_ledger (persona_id, amount, reason, idempotency_key)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (persona_id, idempotency_key) DO NOTHING
				""", personaId, p.reward(), p.title(), p.missionId() + ":" + referenceDate);
		}
		return granted;
	}

	@Transactional(readOnly = true)
	public long points(UUID personaId) {
		Long sum = jdbc.queryForObject(
			"SELECT COALESCE(sum(amount), 0) FROM point_ledger WHERE persona_id = ?", Long.class, personaId);
		return sum == null ? 0 : sum;
	}

	/**
	 * "예산을 지켜라" 도장판.
	 *
	 * 앱이 요일 도트로 보여주는 그것이다. 하루하루의 성패를 원장에서 센다 —
	 * 사용자가 누르는 게 아니라 그날 쓴 금액이 정한다.
	 */
	@Transactional(readOnly = true)
	public List<DayMark> keepStreak(UUID personaId, LocalDate referenceDate) {
		long dailyLimit = PeriodType.DAILY.budgetLimit(targetMonthlySpend(personaId));
		PeriodType.Range week = PeriodType.WEEKLY.range(referenceDate);

		return jdbc.query("""
			WITH days AS (SELECT generate_series(?::date, ?::date, '1 day')::date AS d)
			SELECT d,
			       COALESCE((SELECT -sum(amount) FROM ledger_entry e
			                 WHERE e.persona_id = ? AND e.occurred_on = d AND e.flow = '소비'), 0) AS spent
			FROM days ORDER BY d
			""",
			(rs, i) -> {
				LocalDate d = rs.getObject("d", LocalDate.class);
				long spent = rs.getLong("spent");
				return new DayMark(d, spent, dailyLimit, spent <= dailyLimit);
			},
			week.start(), week.end(), personaId);
	}

	private int judge(UUID personaId, String kind, LocalDate from, LocalDate to) {
		return switch (kind) {
			case "budget-daily" -> {
				// 담은 날부터 지금까지, 하루 예산을 지킨 날의 비율
				List<DayMark> marks = daysBetween(personaId, from, to);
				if (marks.isEmpty()) {
					yield 0;
				}
				long kept = marks.stream().filter(DayMark::kept).count();
				yield (int) Math.round(kept * 100.0 / marks.size());
			}
			case "saving" -> {
				// 담은 뒤 저축한 금액이 월 목표 저축액에 얼마나 닿았는가
				Long saved = jdbc.queryForObject("""
					SELECT COALESCE(-sum(amount), 0) FROM ledger_entry
					WHERE persona_id = ? AND flow = '저축' AND occurred_on BETWEEN ? AND ?
					""", Long.class, personaId, from, to);
				Long goal = jdbc.queryForObject("""
					SELECT round(monthly_income * target_saving_rate) FROM persona WHERE id = ?
					""", Long.class, personaId);
				yield goal == null || goal == 0 ? 0
					: (int) Math.min(100, Math.round((saved == null ? 0 : saved) * 100.0 / goal));
			}
			// 퀴즈처럼 행동으로 끝나는 미션은 원장이 판정할 수 없다. 담은 순간 완료로 본다.
			default -> 100;
		};
	}

	private List<DayMark> daysBetween(UUID personaId, LocalDate from, LocalDate to) {
		long dailyLimit = PeriodType.DAILY.budgetLimit(targetMonthlySpend(personaId));
		return jdbc.query("""
			WITH days AS (SELECT generate_series(?::date, ?::date, '1 day')::date AS d)
			SELECT d,
			       COALESCE((SELECT -sum(amount) FROM ledger_entry e
			                 WHERE e.persona_id = ? AND e.occurred_on = d AND e.flow = '소비'), 0) AS spent
			FROM days
			""",
			(rs, i) -> {
				long spent = rs.getLong("spent");
				return new DayMark(rs.getObject("d", LocalDate.class), spent, dailyLimit, spent <= dailyLimit);
			},
			from, to, personaId);
	}

	private long targetMonthlySpend(UUID personaId) {
		Long v = jdbc.queryForObject(
			"SELECT target_monthly_spend FROM persona WHERE id = ?", Long.class, personaId);
		return v == null ? 0 : v;
	}
}
