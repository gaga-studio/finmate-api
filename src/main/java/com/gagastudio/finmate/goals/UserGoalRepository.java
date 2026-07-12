package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
	Optional<UserGoal> findByUserIdAndState(UUID userId, String state);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select goal from UserGoal goal where goal.userId = :userId and goal.state = 'ACTIVE'")
	Optional<UserGoal> findActiveForUpdate(@Param("userId") UUID userId);
}
