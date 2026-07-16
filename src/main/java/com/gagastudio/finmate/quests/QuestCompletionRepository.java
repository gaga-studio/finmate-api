package com.gagastudio.finmate.quests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestCompletionRepository extends JpaRepository<QuestCompletion, UUID> {
	Optional<QuestCompletion> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
	Optional<QuestCompletion> findByQuestId(UUID questId);
	List<QuestCompletion> findByUserId(UUID userId);
	List<QuestCompletion> findByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
		UUID userId, Instant fromInclusive, Instant toExclusive);
}
