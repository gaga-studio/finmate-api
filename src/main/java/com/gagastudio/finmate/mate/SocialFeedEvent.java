package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_social_feed_event")
class SocialFeedEvent {
	@Id
	private UUID id;
	@Column(name = "friend_id", nullable = false)
	private String friendId;
	@Column(name = "event_type", nullable = false)
	private String eventType;
	@Column(nullable = false)
	private String message;
	@Column(nullable = false)
	private boolean completed;
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected SocialFeedEvent() {
	}

	String getFriendId() { return friendId; }
	String getEventType() { return eventType; }
	String getMessage() { return message; }
	boolean isCompleted() { return completed; }
	Instant getOccurredAt() { return occurredAt; }
}
