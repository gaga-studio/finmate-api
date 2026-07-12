package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DemoTimelineCommandRepository extends JpaRepository<DemoTimelineCommand, UUID> {
	Optional<DemoTimelineCommand> findByUserIdAndFixtureIdAndIdempotencyKey(UUID userId, String fixtureId, String idempotencyKey);
}
