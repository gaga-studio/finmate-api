package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
	Optional<UserGoal> findByUserIdAndState(UUID userId, String state);
	boolean existsByUserIdAndState(UUID userId, String state);
}
