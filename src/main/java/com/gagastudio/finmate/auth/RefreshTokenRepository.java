package com.gagastudio.finmate.auth;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
