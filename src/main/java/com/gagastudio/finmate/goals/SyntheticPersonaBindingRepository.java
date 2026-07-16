package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyntheticPersonaBindingRepository extends JpaRepository<SyntheticPersonaBinding, UUID> {
	Optional<SyntheticPersonaBinding> findByUserId(UUID userId);
}
