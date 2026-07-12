package com.gagastudio.finmate.goals;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyntheticFinancialSnapshotRepository extends JpaRepository<SyntheticFinancialSnapshot, UUID> {
	Optional<SyntheticFinancialSnapshot> findTopByUserIdAndGoalIdAndSnapshotMonthOrderByLastSyncedAtDesc(
		UUID userId, UUID goalId, LocalDate snapshotMonth);
	Optional<SyntheticFinancialSnapshot> findTopByUserIdAndGoalIdOrderByLastSyncedAtDesc(UUID userId, UUID goalId);
}
