package com.gagastudio.finmate.auth;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_refresh")
public class RefreshToken {
	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private FinmateUser user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected RefreshToken() {
	}

	public RefreshToken(FinmateUser user, String tokenHash, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.tokenHash = tokenHash;
		this.issuedAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public FinmateUser getUser() { return user; }
	public Instant getIssuedAt() { return issuedAt; }
	public Instant getExpiresAt() { return expiresAt; }

	public void revoke() {
		this.revokedAt = Instant.now();
	}
}
