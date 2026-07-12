package com.gagastudio.finmate.goals;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

interface DemoFixtureStateRepository extends JpaRepository<DemoFixtureState, DemoFixtureState.DemoFixtureStateId> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<DemoFixtureState> findByIdUserIdAndIdFixtureId(UUID userId, String fixtureId);
}
