package com.gagastudio.finmate.mate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyntheticPublicProfileRepository extends JpaRepository<SyntheticPublicProfile, UUID> {
	List<SyntheticPublicProfile> findByIdInAndConsentState(Collection<UUID> ids, String consentState);
	Optional<SyntheticPublicProfile> findByOwnerUserId(UUID ownerUserId);
}
