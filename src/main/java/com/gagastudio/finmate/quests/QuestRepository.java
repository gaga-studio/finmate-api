package com.gagastudio.finmate.quests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface QuestRepository extends JpaRepository<Quest, UUID> {
	List<Quest> findByUserIdOrderByDisplayOrderAsc(UUID userId);
	Optional<Quest> findByIdAndUserId(UUID id, UUID userId);
	Optional<Quest> findByUserIdAndAcceptIdempotencyKey(UUID userId, String acceptIdempotencyKey);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Quest> findForUpdateByIdAndUserId(UUID id, UUID userId);
}
