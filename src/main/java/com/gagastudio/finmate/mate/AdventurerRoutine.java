package com.gagastudio.finmate.mate;

import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_adventurer_routine")
class AdventurerRoutine {
	@Id
	private String id;
	@Column(name = "group_id", nullable = false)
	private String groupId;
	@Column(name = "adventurer_id", nullable = false)
	private String adventurerId;
	@Column(nullable = false)
	private String title;
	@Column(nullable = false)
	private String description;
	@Column(name = "available_domains", nullable = false)
	private String availableDomains;
	@Column(name = "maintained_days", nullable = false)
	private int maintainedDays;

	protected AdventurerRoutine() {
	}

	String getId() { return id; }
	String getGroupId() { return groupId; }
	String getAdventurerId() { return adventurerId; }
	String getTitle() { return title; }
	String getDescription() { return description; }
	List<String> domains() { return List.of(availableDomains.split(",")); }
	int getMaintainedDays() { return maintainedDays; }
}
