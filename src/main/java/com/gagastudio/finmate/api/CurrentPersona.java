package com.gagastudio.finmate.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.gagastudio.finmate.ledger.PersonaAssignment;

/**
 * 지금 로그인한 사람의 원장이 누구 것인지 알려준다.
 *
 * 컨트롤러가 매번 토큰에서 사용자 id를 꺼내고 persona를 조회하는 코드를 반복하면,
 * 그중 하나가 빠졌을 때 남의 원장을 보게 된다. 한 곳으로 모은다.
 */
@Component
public class CurrentPersona {

	private final PersonaAssignment assignment;

	public CurrentPersona(PersonaAssignment assignment) {
		this.assignment = assignment;
	}

	public UUID userId() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
		}
		return UUID.fromString(jwt.getSubject());
	}

	/** 배정된 원장이 없으면 화면이 통째로 빈다. 그 상태를 조용히 넘기지 않는다. */
	public UUID personaId() {
		return assignment.personaOf(userId())
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.CONFLICT, "이 계정에 연결된 금융 데이터가 없습니다"));
	}
}
