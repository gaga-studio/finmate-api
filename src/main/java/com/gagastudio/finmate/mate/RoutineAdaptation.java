package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_routine_adaptation")
class RoutineAdaptation {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "group_id", nullable = false)
	private String groupId;
	@Column(name = "adventurer_id", nullable = false)
	private String adventurerId;
	@Column(name = "source_routine_id", nullable = false)
	private String sourceRoutineId;
	@Column(nullable = false)
	private String state;
	@Column(name = "selected_domain")
	private String selectedDomain;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected RoutineAdaptation() {
	}

	RoutineAdaptation(UUID userId, AdventurerRoutine routine, Instant now) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.groupId = routine.getGroupId();
		this.adventurerId = routine.getAdventurerId();
		this.sourceRoutineId = routine.getId();
		this.state = "AWAITING_DOMAIN";
		this.createdAt = now;
		this.updatedAt = now;
	}

	void selectDomain(String domain, Instant now) {
		this.selectedDomain = domain;
		this.state = "CANDIDATES_READY";
		this.updatedAt = now;
	}

	UUID getId() { return id; }
	String getSourceRoutineId() { return sourceRoutineId; }
	String getState() { return state; }
	String getSelectedDomain() { return selectedDomain; }
}
