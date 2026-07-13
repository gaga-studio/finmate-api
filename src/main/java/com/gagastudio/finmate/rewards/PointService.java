package com.gagastudio.finmate.rewards;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
	private final PointLedgerRepository ledger;
	private final CosmeticCatalogRepository catalog;
	private final UserCosmeticRepository ownedCosmetics;
	private final JdbcTemplate jdbcTemplate;

	PointService(PointLedgerRepository ledger, CosmeticCatalogRepository catalog,
		UserCosmeticRepository ownedCosmetics, JdbcTemplate jdbcTemplate) {
		this.ledger = ledger;
		this.catalog = catalog;
		this.ownedCosmetics = ownedCosmetics;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public int awardQuestPoints(UUID userId, UUID questId, int points, Instant occurredAt) {
		if (points <= 0) return 0;
		String sourceId = questId.toString();
		if (ledger.existsByUserIdAndSourceTypeAndSourceId(userId, "QUEST", sourceId)) return points;
		ledger.save(new PointLedgerEntry(userId, "EARN", points, "QUEST", sourceId,
			"quest-point-" + sourceId, occurredAt));
		return points;
	}

	@Transactional(readOnly = true)
	RewardDtos.PointLedgerView ledger(UUID userId) {
		List<PointLedgerEntry> entries = ledger.findByUserIdOrderByOccurredAtDesc(userId);
		return new RewardDtos.PointLedgerView(balance(entries), entries.stream().map(entry ->
			new RewardDtos.PointLedgerEntryView(entry.getEntryType(), entry.getAmountPoints(), entry.getSourceType(),
				entry.getSourceId(), entry.getOccurredAt())).toList());
	}

	@Transactional(readOnly = true)
	RewardDtos.CosmeticCatalogView catalog(UUID userId) {
		return new RewardDtos.CosmeticCatalogView(catalog.findByAvailableTrueOrderByDisplayOrder().stream()
			.map(item -> new RewardDtos.CosmeticCatalogItemView(item.getId(), item.getItemType(), item.getName(),
				item.getDescription(), item.getPricePoints(),
				ownedCosmetics.existsByIdUserIdAndIdCosmeticId(userId, item.getId())))
			.toList());
	}

	@Transactional
	RewardDtos.CosmeticPurchaseView purchase(UUID userId, String cosmeticId, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new RewardException("VALIDATION_FAILED", "Idempotency-Key must be 16 to 128 characters");
		}
		jdbcTemplate.queryForObject("SELECT id FROM finmate_user WHERE id = ? FOR UPDATE", UUID.class, userId);
		PointLedgerEntry replay = ledger.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
		if (replay != null) {
			if (!replay.getSourceId().equals(cosmeticId)) {
				throw new RewardException("IDEMPOTENCY_KEY_REUSED", "Idempotency key belongs to another purchase");
			}
			return new RewardDtos.CosmeticPurchaseView(cosmeticId, true, currentBalance(userId));
		}
		CosmeticCatalogItem item = catalog.findById(cosmeticId).filter(CosmeticCatalogItem::isAvailable)
			.orElseThrow(() -> new RewardException("NOT_FOUND", "Cosmetic item was not found"));
		if (ownedCosmetics.existsByIdUserIdAndIdCosmeticId(userId, cosmeticId)) {
			throw new RewardException("ALREADY_OWNED", "Cosmetic item is already owned");
		}
		int current = currentBalance(userId);
		if (current < item.getPricePoints()) {
			throw new RewardException("INSUFFICIENT_POINTS", "Not enough cosmetic points");
		}
		Instant now = Instant.now();
		ledger.save(new PointLedgerEntry(userId, "SPEND", -item.getPricePoints(), "COSMETIC", cosmeticId,
			idempotencyKey, now));
		ownedCosmetics.save(new UserCosmetic(userId, cosmeticId, now));
		return new RewardDtos.CosmeticPurchaseView(cosmeticId, true, current - item.getPricePoints());
	}

	private int currentBalance(UUID userId) {
		return balance(ledger.findByUserIdOrderByOccurredAtDesc(userId));
	}

	private int balance(List<PointLedgerEntry> entries) {
		return entries.stream().mapToInt(PointLedgerEntry::getAmountPoints).sum();
	}
}
