package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.List;

final class SocialMateDtos {
	private SocialMateDtos() {
	}

	record FriendOverview(int friendCount, int completedToday, boolean readOnly, List<FriendSummary> friends) {
	}

	record FriendSummary(String friendId, String alias, String avatarCode, boolean questCompletedToday) {
	}

	record FeedPage(boolean readOnly, List<FeedItem> items) {
	}

	record FeedItem(String friendId, String alias, String avatarCode, String eventType,
		String message, boolean completed, Instant occurredAt) {
	}

	record StreakPage(boolean readOnly, List<StreakItem> items) {
	}

	record StreakItem(String friendId, String alias, String label, int daysTogether) {
	}
}
