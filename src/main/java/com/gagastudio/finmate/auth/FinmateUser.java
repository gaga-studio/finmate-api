package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 로그인에 필요한 최소 정보만 갖는다.
 *
 * 이전 vNext 버전은 여기에 온보딩 설문 10개 · 알림/모션 취향 4개 · 공개 프로필 동의 7개까지
 * 총 24개 컬럼을 얹고 있었다. 그 화면들이 없어졌으므로 같이 걷어냈다.
 *
 * 금융 프로필(연령·직업·지역·월소득·저축 목표율·위험성향)은 여기 두지 않는다.
 * 그건 인증이 아니라 원장 쪽 관심사이고, 별도 테이블로 분리해야 원장 적재와 인증이 서로를
 * 끌고 다니지 않는다.
 */
@Entity
@Table(name = "finmate_user")
public class FinmateUser {
	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FinmateUser() {
	}

	public FinmateUser(String email, String displayName, String passwordHash) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void rename(String displayName) {
		this.displayName = displayName;
	}
}
