package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class PublicFinancialProfileDtos {
	private PublicFinancialProfileDtos() {
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	record PublicFinancialProfile(String adventurerId, String alias, boolean synthetic, boolean exactValues,
		List<String> visibleFields, String consentVersion, Instant updatedAt,
		List<PublicAssetEntry> assets, List<PublicCashflowEntry> income, List<PublicCashflowEntry> spending,
		List<PublicCashflowEntry> savings, List<PublicProductHolding> products,
		List<PublicInvestmentHolding> investments, List<PublicTradeRecord> trades) {
	}

	record PublicAssetEntry(String name, String category, long balanceKrw, LocalDate asOfDate) {
	}

	record PublicCashflowEntry(String name, String category, long amountKrw, LocalDate asOfDate) {
	}

	record PublicProductHolding(String productName, String category, long balanceKrw, LocalDate asOfDate) {
	}

	record PublicInvestmentHolding(String name, String ticker, long balanceKrw, int allocationBps,
		BigDecimal quantity, LocalDate asOfDate) {
	}

	record PublicTradeRecord(String name, String ticker, String action, long amountKrw,
		BigDecimal quantity, Instant occurredAt) {
	}
}
