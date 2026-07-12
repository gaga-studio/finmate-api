package com.gagastudio.finmate.mate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoutineBuildRepository extends JpaRepository<RoutineBuild, UUID> {
	Optional<RoutineBuild> findByUserIdAndStatus(UUID userId, String status);
}
