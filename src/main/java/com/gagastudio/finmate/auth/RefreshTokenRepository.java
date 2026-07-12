package com.gagastudio.finmate.auth;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
