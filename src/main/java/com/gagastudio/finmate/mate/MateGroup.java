package com.gagastudio.finmate.mate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_mate_group")
class MateGroup {
	@Id
	private String id;
	@Column(nullable = false)
	private String name;
	@Column(name = "member_count", nullable = false)
	private int memberCount;
	@Column(name = "synthetic_demo", nullable = false)
	private boolean syntheticDemo;
	@Column(name = "eligible_for_production_aggregation", nullable = false)
	private boolean eligibleForProductionAggregation;

	protected MateGroup() {
	}

	String getId() { return id; }
	String getName() { return name; }
	int getMemberCount() { return memberCount; }
	boolean isSyntheticDemo() { return syntheticDemo; }
	boolean isEligibleForProductionAggregation() { return eligibleForProductionAggregation; }
}
