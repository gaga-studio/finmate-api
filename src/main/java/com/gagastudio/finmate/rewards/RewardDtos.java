package com.gagastudio.finmate.rewards;

import java.time.Instant;
import java.util.List;

final class RewardDtos {
	private RewardDtos() {
	}

	record PointLedgerView(int balance, List<PointLedgerEntryView> entries) {
	}

	record PointLedgerEntryView(String entryType, int amountPoints, String sourceType, String sourceId, Instant occurredAt) {
	}

	record CosmeticCatalogView(List<CosmeticCatalogItemView> items) {
	}

	record CosmeticCatalogItemView(String id, String itemType, String name, String description,
		int pricePoints, boolean owned) {
	}

	record CosmeticPurchaseView(String cosmeticId, boolean owned, int balance) {
	}
}
