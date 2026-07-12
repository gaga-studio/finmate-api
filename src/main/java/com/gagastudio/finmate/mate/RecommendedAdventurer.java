package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_recommended_adventurer")
class RecommendedAdventurer {
	@Id
	private String id;
	@Column(name = "group_id", nullable = false)
	private String groupId;
	@Column(nullable = false)
	private String alias;
	@Column(name = "similarity_reasons", nullable = false)
	private String similarityReasons;
	@Column(name = "approved_at", nullable = false)
	private Instant approvedAt;

	protected RecommendedAdventurer() {
	}

	String getId() { return id; }
	String getGroupId() { return groupId; }
	String getAlias() { return alias; }
	List<String> reasons() { return List.of(similarityReasons.split("\\|")); }
	Instant getApprovedAt() { return approvedAt; }
}
