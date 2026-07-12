package com.gagastudio.finmate.goals;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyntheticFinancialSnapshotRepository extends JpaRepository<SyntheticFinancialSnapshot, UUID> {
	Optional<SyntheticFinancialSnapshot> findTopByUserIdAndSnapshotMonthOrderByLastSyncedAtDesc(UUID userId, LocalDate snapshotMonth);
	Optional<SyntheticFinancialSnapshot> findTopByUserIdOrderByLastSyncedAtDesc(UUID userId);
}
