package com.gagastudio.finmate.goals;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_synthetic_runtime_persona")
class SyntheticRuntimePersona {
	@EmbeddedId
	private SyntheticRuntimePersonaId id;
	@Column(name = "age_band", nullable = false)
	private String ageBand;
	@Column(name = "occupation_group", nullable = false)
	private String occupationGroup;
	@Column(name = "income_band", nullable = false)
	private String incomeBand;
	@Column(name = "spending_tendency", nullable = false)
	private String spendingTendency;
	@Column(name = "saving_rate_band", nullable = false)
	private String savingRateBand;
	@Column(name = "investment_tendency", nullable = false)
	private String investmentTendency;
	@Column(name = "income_regularity", nullable = false)
	private String incomeRegularity;
	@Column(name = "household_type", nullable = false)
	private String householdType;
	@Column(name = "lifestyle_tags", nullable = false)
	private String lifestyleTags;
	@Column(name = "money_worry", nullable = false)
	private String moneyWorry;
	@Column(name = "peer_discovery_opt_in", nullable = false)
	private boolean peerDiscoveryOptIn;
	@Column(name = "data_state", nullable = false)
	private String dataState;
	@Column(name = "last_synced_at", nullable = false)
	private java.time.Instant lastSyncedAt;

	protected SyntheticRuntimePersona() {
	}

	String getSourcePersonaId() { return id.getSourcePersonaId(); }
	String getAgeBand() { return ageBand; }
	String getOccupationGroup() { return occupationGroup; }
	String getIncomeBand() { return incomeBand; }
	String getSpendingTendency() { return spendingTendency; }
	String getSavingRateBand() { return savingRateBand; }
	String getInvestmentTendency() { return investmentTendency; }
	String getIncomeRegularity() { return incomeRegularity; }
	String getHouseholdType() { return householdType; }
	String getLifestyleTags() { return lifestyleTags; }
	String getMoneyWorry() { return moneyWorry; }
	String getDataState() { return dataState; }
	java.time.Instant getLastSyncedAt() { return lastSyncedAt; }
}
