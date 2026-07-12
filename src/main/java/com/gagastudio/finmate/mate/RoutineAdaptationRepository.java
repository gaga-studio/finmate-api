package com.gagastudio.finmate.mate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoutineAdaptationRepository extends JpaRepository<RoutineAdaptation, UUID> {
	Optional<RoutineAdaptation> findByIdAndUserId(UUID id, UUID userId);
}
