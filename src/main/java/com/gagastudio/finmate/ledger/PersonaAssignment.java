package com.gagastudio.finmate.ledger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입한 사람에게 합성 인구 한 명을 붙인다.
 *
 * 이 서비스는 마이데이터를 연동해 진짜 거래를 받아오는 것이 전제다. 그 연동이 없는 지금,
 * 가입 직후 화면이 비어 있으면 아무것도 확인할 수 없다. 그래서 합성 인구 중 한 명을 준다.
 *
 * 한 사람을 두 계정이 나눠 갖지 않는다. 같은 원장을 둘이 보면 또래 비교의 인원 수가 어긋나고,
 * 한쪽이 그림일기를 만들면 다른 쪽 화면에 갑자기 나타난다.
 */
@Service
public class PersonaAssignment {

	private final JdbcTemplate jdbc;

	public PersonaAssignment(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 아직 아무도 안 가진 인구 중 하나를 배정한다.
	 *
	 * 고르는 것과 붙이는 것을 한 문장으로 한다. 나눠 하면 두 가입이 같은 사람을 고를 수 있고,
	 * 그때는 유니크 제약에 걸려 가입이 실패한다 — 남은 사람이 있는데 가입이 안 되는 셈이다.
	 * {@code FOR UPDATE SKIP LOCKED}로 서로 다른 사람을 집게 한다.
	 *
	 * @return 배정된 persona. 남은 사람이 없으면 비어 있다.
	 */
	@Transactional
	public Optional<UUID> assign(UUID userId) {
		try {
			UUID personaId = jdbc.queryForObject("""
				UPDATE finmate_user SET persona_id = (
				  SELECT p.id FROM persona p
				  WHERE NOT EXISTS (SELECT 1 FROM finmate_user u WHERE u.persona_id = p.id)
				  ORDER BY p.external_id LIMIT 1
				  FOR UPDATE SKIP LOCKED
				)
				WHERE id = ? AND persona_id IS NULL
				RETURNING persona_id
				""", UUID.class, userId);
			return Optional.ofNullable(personaId);
		} catch (EmptyResultDataAccessException e) {
			// 이미 배정돼 있거나 없는 사용자다
			return personaOf(userId);
		}
	}

	@Transactional(readOnly = true)
	public Optional<UUID> personaOf(UUID userId) {
		try {
			return Optional.ofNullable(jdbc.queryForObject(
				"SELECT persona_id FROM finmate_user WHERE id = ?", UUID.class, userId));
		} catch (EmptyResultDataAccessException e) {
			return Optional.empty();
		}
	}
}
