package com.gagastudio.finmate.mate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_social_friend")
class SocialFriend {
	@Id
	private String id;
	@Column(nullable = false)
	private String alias;
	@Column(name = "avatar_code", nullable = false)
	private String avatarCode;
	@Column(name = "quest_completed_today", nullable = false)
	private boolean questCompletedToday;
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected SocialFriend() {
	}

	String getId() { return id; }
	String getAlias() { return alias; }
	String getAvatarCode() { return avatarCode; }
	boolean isQuestCompletedToday() { return questCompletedToday; }
}
