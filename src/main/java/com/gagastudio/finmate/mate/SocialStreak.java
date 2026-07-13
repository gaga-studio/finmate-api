package com.gagastudio.finmate.mate;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_social_streak")
class SocialStreak {
	@Id
	private UUID id;
	@Column(name = "friend_id", nullable = false)
	private String friendId;
	@Column(nullable = false)
	private String label;
	@Column(name = "days_together", nullable = false)
	private int daysTogether;
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected SocialStreak() {
	}

	String getFriendId() { return friendId; }
	String getLabel() { return label; }
	int getDaysTogether() { return daysTogether; }
}
