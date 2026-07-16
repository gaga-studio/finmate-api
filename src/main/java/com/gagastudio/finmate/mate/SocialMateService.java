package com.gagastudio.finmate.mate;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SocialMateService {
	private final SocialRuntimeRepository runtime;

	SocialMateService(SocialRuntimeRepository runtime) {
		this.runtime = runtime;
	}

	@Transactional(readOnly = true)
	SocialMateDtos.FriendOverview overview(UUID userId) {
		return runtime.scope(userId).map(scope -> {
			var friends = runtime.friends(scope);
			return new SocialMateDtos.FriendOverview(friends.size(),
				(int) friends.stream().filter(SocialRuntimeFriend::questCompletedToday).count(), true,
				friends.stream().map(friend -> new SocialMateDtos.FriendSummary(friend.publicId(), friend.alias(),
					friend.avatarCode(), friend.questCompletedToday())).toList());
		}).orElseGet(() -> new SocialMateDtos.FriendOverview(0, 0, true, List.of()));
	}

	@Transactional(readOnly = true)
	SocialMateDtos.FeedPage feed(UUID userId) {
		return runtime.scope(userId).map(scope -> new SocialMateDtos.FeedPage(true,
			runtime.feed(scope).stream().map(event -> new SocialMateDtos.FeedItem(
				event.publicId(), event.alias(), event.avatarCode(), apiEventType(event), safeMessage(event),
				"goal_stage_clear".equals(event.eventType()),
				event.eventDate().atStartOfDay().toInstant(ZoneOffset.UTC))).toList()))
			.orElseGet(() -> new SocialMateDtos.FeedPage(true, List.of()));
	}

	@Transactional(readOnly = true)
	SocialMateDtos.StreakPage streaks(UUID userId) {
		return runtime.scope(userId).map(scope -> new SocialMateDtos.StreakPage(true,
			runtime.streaks(scope).stream().map(streak -> new SocialMateDtos.StreakItem(
				streak.publicId(), streak.alias(), "친구와 함께한 연속기록", streak.currentStreak())).toList()))
			.orElseGet(() -> new SocialMateDtos.StreakPage(true, List.of()));
	}

	private String apiEventType(SocialRuntimeFeedEvent event) {
		return "goal_stage_clear".equals(event.eventType()) ? "QUEST" : "ROUTINE";
	}

	private String safeMessage(SocialRuntimeFeedEvent event) {
		return switch (event.eventType()) {
			case "goal_stage_clear" -> event.alias() + "님이 목표 레이드 단계를 완료했어요";
			case "stat_up:defense_score" -> event.alias() + "님이 소비 관리 습관을 이어가고 있어요";
			case "stat_up:saving_score" -> event.alias() + "님이 저축 습관을 이어가고 있어요";
			default -> "친구의 금융 습관 기록이 업데이트됐어요";
		};
	}
}
