package com.gagastudio.finmate.mate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_public_financial_item")
class PublicFinancialItem {
	@Id
	private UUID id;
	@Column(name = "public_profile_id", nullable = false)
	private UUID publicProfileId;
	@Column(name = "field_name", nullable = false)
	private String fieldName;
	@Column(name = "display_name", nullable = false)
	private String displayName;
	private String category;
	@Column(name = "amount_krw")
	private Long amountKrw;
	@Column(name = "balance_krw")
	private Long balanceKrw;
	private String ticker;
	@Column(name = "allocation_bps")
	private Integer allocationBps;
	private BigDecimal quantity;
	private String action;
	@Column(name = "occurred_at")
	private Instant occurredAt;
	@Column(name = "as_of_date")
	private LocalDate asOfDate;
	@Column(name = "item_order", nullable = false)
	private int itemOrder;

	protected PublicFinancialItem() {
	}

	String getFieldName() { return fieldName; }
	String getDisplayName() { return displayName; }
	String getCategory() { return category; }
	Long getAmountKrw() { return amountKrw; }
	Long getBalanceKrw() { return balanceKrw; }
	String getTicker() { return ticker; }
	Integer getAllocationBps() { return allocationBps; }
	BigDecimal getQuantity() { return quantity; }
	String getAction() { return action; }
	Instant getOccurredAt() { return occurredAt; }
	LocalDate getAsOfDate() { return asOfDate; }
}
