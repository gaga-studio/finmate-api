package com.gagastudio.finmate.diary;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그림일기 요청을 받아 둔다. 그림을 만들지는 않는다 — 그건 워커의 일이다.
 *
 * 하루에 한 장이 이 기능의 규칙이다. 같은 날 요청이 여러 번 와도, 동시에 와도 그림은 하나다.
 * 애플리케이션에서 "있는지 보고 없으면 넣는다"로 처리하면 두 요청이 동시에 통과할 수 있어,
 * DB 유니크 제약에 맡기고 `ON CONFLICT DO NOTHING`으로 받는다.
 */
@Service
public class DiaryService {

	private final JdbcTemplate jdbc;

	public DiaryService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Entry(
		UUID personaId, LocalDate date, String status,
		String dominantFlow, String subject, String artStyle,
		String imagePath, int attempts, String lastError) {
	}

	/**
	 * 그날의 그림을 요청한다. 이미 있으면 있는 것을 그대로 돌려준다.
	 *
	 * @return 이 호출이 새로 만들었으면 true
	 */
	@Transactional
	public boolean request(UUID personaId, LocalDate date) {
		Dominant dominant = dominantOf(personaId, date);
		ArtStyle style = ArtStyle.forDate(date);
		String prompt = DiaryPrompt.build(style, dominant.category());

		int inserted = jdbc.update("""
			INSERT INTO diary_entry
			  (persona_id, entry_date, dominant_flow, subject, art_style, prompt, status)
			VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
			ON CONFLICT (persona_id, entry_date) DO NOTHING
			""",
			personaId, date, dominant.flow(), dominant.category(), style.name(), prompt);
		return inserted > 0;
	}

	@Transactional(readOnly = true)
	public Optional<Entry> find(UUID personaId, LocalDate date) {
		try {
			return Optional.of(jdbc.queryForObject("""
				SELECT persona_id, entry_date, status, dominant_flow, subject, art_style,
				       image_path, attempts, last_error
				FROM diary_entry WHERE persona_id = ? AND entry_date = ?
				""",
				(rs, i) -> new Entry(
					rs.getObject("persona_id", UUID.class),
					rs.getObject("entry_date", LocalDate.class),
					rs.getString("status"),
					rs.getString("dominant_flow"),
					rs.getString("subject"),
					rs.getString("art_style"),
					rs.getString("image_path"),
					rs.getInt("attempts"),
					rs.getString("last_error")),
				personaId, date));
		} catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}

	private record Dominant(String flow, String category) {
	}

	/**
	 * 그날의 주인공을 뽑는다.
	 *
	 * 앱의 `getDayDominant`는 지출을 소비/저축/투자로 합산해 가장 큰 것을 골랐다. 같은 규칙이되
	 * 그림을 그리려면 흐름만으로는 부족해서(‘소비’는 그림이 안 된다) 그 안에서 가장 크게 쓴
	 * 카테고리까지 내려간다.
	 */
	private Dominant dominantOf(UUID personaId, LocalDate date) {
		Map<String, Object> flow = jdbc.queryForMap("""
			SELECT flow, -sum(amount) AS total
			FROM ledger_entry
			WHERE persona_id = ? AND occurred_on = ? AND flow IN ('소비', '저축', '투자')
			GROUP BY flow ORDER BY total DESC LIMIT 1
			""", personaId, date);

		String winner = (String) flow.get("flow");
		String category = jdbc.queryForObject("""
			SELECT category FROM ledger_entry
			WHERE persona_id = ? AND occurred_on = ? AND flow = ?
			GROUP BY category ORDER BY -sum(amount) DESC LIMIT 1
			""", String.class, personaId, date, winner);

		return new Dominant(winner, category);
	}

	/** 거래가 아예 없는 날은 그림도 없다. 없는 하루를 지어내지 않는다. */
	@Transactional(readOnly = true)
	public boolean hasLedger(UUID personaId, LocalDate date) {
		Long n = jdbc.queryForObject("""
			SELECT count(*) FROM ledger_entry
			WHERE persona_id = ? AND occurred_on = ? AND flow IN ('소비', '저축', '투자')
			""", Long.class, personaId, date);
		return n != null && n > 0;
	}
}
