package com.gagastudio.finmate.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FinmateUserRepository extends JpaRepository<FinmateUser, UUID> {
	Optional<FinmateUser> findByEmail(String email);
	boolean existsByEmail(String email);
}
