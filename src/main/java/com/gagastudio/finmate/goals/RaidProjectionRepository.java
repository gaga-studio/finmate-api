package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RaidProjectionRepository extends JpaRepository<RaidProjection, UUID> {
	Optional<RaidProjection> findByUserIdAndGoalId(UUID userId, UUID goalId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select raid from RaidProjection raid where raid.userId = :userId and raid.goalId = :goalId")
	Optional<RaidProjection> findForUpdate(@Param("userId") UUID userId, @Param("goalId") UUID goalId);
}
