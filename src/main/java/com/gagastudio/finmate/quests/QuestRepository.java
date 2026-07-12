package com.gagastudio.finmate.quests;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestRepository extends JpaRepository<Quest, UUID> {
	List<Quest> findByUserIdOrderByDisplayOrderAsc(UUID userId);
	Optional<Quest> findByIdAndUserId(UUID id, UUID userId);
}
