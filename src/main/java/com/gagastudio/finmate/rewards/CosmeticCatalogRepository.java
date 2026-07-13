package com.gagastudio.finmate.rewards;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CosmeticCatalogRepository extends JpaRepository<CosmeticCatalogItem, String> {
	List<CosmeticCatalogItem> findByAvailableTrueOrderByDisplayOrder();
}
