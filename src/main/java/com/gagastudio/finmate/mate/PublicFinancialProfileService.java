package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublicFinancialProfileService {
	private final RecommendedAdventurerRepository adventurers;
	private final SyntheticPublicProfileRepository profiles;
	private final PublicFinancialItemRepository items;
	private final ObjectMapper objectMapper;

	PublicFinancialProfileService(RecommendedAdventurerRepository adventurers,
		SyntheticPublicProfileRepository profiles, PublicFinancialItemRepository items, ObjectMapper objectMapper) {
		this.adventurers = adventurers;
		this.profiles = profiles;
		this.items = items;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	PublicFinancialProfileDtos.PublicFinancialProfile profile(String groupId, String adventurerId) {
		RecommendedAdventurer adventurer = adventurers.findById(adventurerId)
			.filter(candidate -> candidate.getGroupId().equals(groupId))
			.orElseThrow(MateNotFoundException::new);
		SyntheticPublicProfile profile = profiles.findById(adventurer.getPublicProfileId())
			.filter(candidate -> "ACTIVE".equals(candidate.getConsentState()))
			.orElseThrow(MateNotFoundException::new);
		List<String> visibleFields = visibleFields(profile);
		Set<String> visible = Set.copyOf(visibleFields);
		List<PublicFinancialItem> source = items.findByPublicProfileIdOrderByItemOrder(profile.getId());

		List<PublicFinancialProfileDtos.PublicAssetEntry> assets = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicCashflowEntry> income = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicCashflowEntry> spending = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicCashflowEntry> savings = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicProductHolding> products = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicInvestmentHolding> investments = new ArrayList<>();
		List<PublicFinancialProfileDtos.PublicTradeRecord> trades = new ArrayList<>();

		for (PublicFinancialItem item : source) {
			if (!visible.contains(item.getFieldName())) continue;
			switch (item.getFieldName()) {
				case "ASSETS" -> assets.add(new PublicFinancialProfileDtos.PublicAssetEntry(item.getDisplayName(),
					item.getCategory(), value(item.getBalanceKrw()), item.getAsOfDate()));
				case "INCOME" -> income.add(cashflow(item));
				case "SPENDING" -> spending.add(cashflow(item));
				case "SAVING" -> savings.add(cashflow(item));
				case "FINANCIAL_PRODUCTS" -> products.add(new PublicFinancialProfileDtos.PublicProductHolding(
					item.getDisplayName(), item.getCategory(), value(item.getBalanceKrw()), item.getAsOfDate()));
				case "INVESTMENT_HOLDINGS" -> investments.add(new PublicFinancialProfileDtos.PublicInvestmentHolding(
					item.getDisplayName(), item.getTicker(), value(item.getBalanceKrw()), integer(item.getAllocationBps()),
					item.getQuantity(), item.getAsOfDate()));
				case "TRADES" -> trades.add(new PublicFinancialProfileDtos.PublicTradeRecord(item.getDisplayName(),
					item.getTicker(), item.getAction(), value(item.getAmountKrw()), item.getQuantity(), item.getOccurredAt()));
				default -> throw new IllegalStateException("Unsupported disclosure field: " + item.getFieldName());
			}
		}

		return new PublicFinancialProfileDtos.PublicFinancialProfile(adventurerId, profile.getAlias(),
			profile.isSynthetic(), profile.isExactValues(), visibleFields, profile.getConsentVersion(), profile.getUpdatedAt(),
			List.copyOf(assets), List.copyOf(income), List.copyOf(spending), List.copyOf(savings), List.copyOf(products),
			List.copyOf(investments), List.copyOf(trades));
	}

	private PublicFinancialProfileDtos.PublicCashflowEntry cashflow(PublicFinancialItem item) {
		return new PublicFinancialProfileDtos.PublicCashflowEntry(item.getDisplayName(), item.getCategory(),
			value(item.getAmountKrw()), item.getAsOfDate());
	}

	private List<String> visibleFields(SyntheticPublicProfile profile) {
		try {
			return List.of(objectMapper.readValue(profile.getVisibleFields(), String[].class));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored public profile disclosure fields are invalid", exception);
		}
	}

	private long value(Long value) {
		return Objects.requireNonNull(value, "Public financial value is required");
	}

	private int integer(Integer value) {
		return Objects.requireNonNull(value, "Public financial allocation is required");
	}
}
