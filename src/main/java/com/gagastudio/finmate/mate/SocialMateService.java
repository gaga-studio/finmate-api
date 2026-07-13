package com.gagastudio.finmate.mate;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SocialMateService {
	private final SocialFriendRepository friends;
	private final SocialFeedEventRepository feedEvents;
	private final SocialStreakRepository streaks;

	SocialMateService(SocialFriendRepository friends, SocialFeedEventRepository feedEvents,
		SocialStreakRepository streaks) {
		this.friends = friends;
		this.feedEvents = feedEvents;
		this.streaks = streaks;
	}

	@Transactional(readOnly = true)
	SocialMateDtos.FriendOverview overview() {
		var source = friends.findAllByOrderByDisplayOrder();
		return new SocialMateDtos.FriendOverview(source.size(), Math.toIntExact(friends.countByQuestCompletedTodayTrue()),
			true, source.stream().map(friend -> new SocialMateDtos.FriendSummary(friend.getId(), friend.getAlias(),
				friend.getAvatarCode(), friend.isQuestCompletedToday())).toList());
	}

	@Transactional(readOnly = true)
	SocialMateDtos.FeedPage feed() {
		Map<String, SocialFriend> byId = friends.findAllByOrderByDisplayOrder().stream()
			.collect(Collectors.toMap(SocialFriend::getId, Function.identity()));
		return new SocialMateDtos.FeedPage(true, feedEvents.findAllByOrderByDisplayOrder().stream().map(event -> {
			SocialFriend friend = requiredFriend(byId, event.getFriendId());
			return new SocialMateDtos.FeedItem(friend.getId(), friend.getAlias(), friend.getAvatarCode(),
				event.getEventType(), event.getMessage(), event.isCompleted(), event.getOccurredAt());
		}).toList());
	}

	@Transactional(readOnly = true)
	SocialMateDtos.StreakPage streaks() {
		Map<String, SocialFriend> byId = friends.findAllByOrderByDisplayOrder().stream()
			.collect(Collectors.toMap(SocialFriend::getId, Function.identity()));
		return new SocialMateDtos.StreakPage(true, streaks.findAllByOrderByDisplayOrder().stream().map(streak -> {
			SocialFriend friend = requiredFriend(byId, streak.getFriendId());
			return new SocialMateDtos.StreakItem(friend.getId(), friend.getAlias(), streak.getLabel(), streak.getDaysTogether());
		}).toList());
	}

	private SocialFriend requiredFriend(Map<String, SocialFriend> friends, String friendId) {
		SocialFriend friend = friends.get(friendId);
		if (friend == null) throw new IllegalStateException("Synthetic social projection references a missing friend");
		return friend;
	}
}
