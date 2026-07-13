package com.gagastudio.finmate.rewards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_cosmetic_catalog")
class CosmeticCatalogItem {
	@Id
	private String id;
	@Column(name = "item_type", nullable = false)
	private String itemType;
	@Column(nullable = false)
	private String name;
	@Column(nullable = false)
	private String description;
	@Column(name = "price_points", nullable = false)
	private int pricePoints;
	@Column(nullable = false)
	private boolean available;
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected CosmeticCatalogItem() {
	}

	String getId() { return id; }
	String getItemType() { return itemType; }
	String getName() { return name; }
	String getDescription() { return description; }
	int getPricePoints() { return pricePoints; }
	boolean isAvailable() { return available; }
}
