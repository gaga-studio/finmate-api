package com.gagastudio.finmate.rewards;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PointLedgerRepository extends JpaRepository<PointLedgerEntry, UUID> {
	List<PointLedgerEntry> findByUserIdOrderByOccurredAtDesc(UUID userId);
	Optional<PointLedgerEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
	boolean existsByUserIdAndSourceTypeAndSourceId(UUID userId, String sourceType, String sourceId);
}
