package com.gagastudio.finmate.rewards;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserCosmeticRepository extends JpaRepository<UserCosmetic, UserCosmetic.UserCosmeticId> {
	boolean existsByIdUserIdAndIdCosmeticId(UUID userId, String cosmeticId);
}
