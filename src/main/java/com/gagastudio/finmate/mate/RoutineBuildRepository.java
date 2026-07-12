package com.gagastudio.finmate.mate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoutineBuildRepository extends JpaRepository<RoutineBuild, UUID> {
	Optional<RoutineBuild> findByUserIdAndStatus(UUID userId, String status);
	Optional<RoutineBuild> findByUserIdAndCommandTypeAndIdempotencyKey(UUID userId, String commandType, String idempotencyKey);
	Optional<RoutineBuild> findByIdAndUserId(UUID id, UUID userId);
}
