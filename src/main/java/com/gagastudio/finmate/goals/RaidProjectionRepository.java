package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RaidProjectionRepository extends JpaRepository<RaidProjection, UUID> {
	Optional<RaidProjection> findByGoalId(UUID goalId);
}
