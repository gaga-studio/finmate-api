package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublicFinancialItemRepository extends JpaRepository<PublicFinancialItem, UUID> {
	List<PublicFinancialItem> findByPublicProfileIdOrderByItemOrder(UUID publicProfileId);
}
