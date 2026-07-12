package com.gagastudio.finmate.quests;

import com.gagastudio.finmate.records.RecordService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestService {
	private static final List<QuestSeed> SEEDS = List.of(
		new QuestSeed("DAILY_SPENDING_CHECK", "Daily spending check", "BEHAVIOR", 10, "DAILY_SPENDING_BADGE"),
		new QuestSeed("WEEKLY_SAVING_PLAN", "Weekly saving plan", "BEHAVIOR", 20, "WEEKLY_SAVING_BADGE"),
		new QuestSeed("PUBLIC_TRANSIT", "Choose public transit", "BEHAVIOR", 15, "TRANSIT_STREAK_TOKEN"),
		new QuestSeed("RISK_PROFILE", "Complete risk-profile diagnosis", "BEHAVIOR", 20, "RISK_PROFILE_COMPLETE"),
		new QuestSeed("ETF_OX_QUIZ", "ETF O/X knowledge quiz", "BEHAVIOR", 15, "ETF_KNOWLEDGE_TOKEN"),
		new QuestSeed("KNOWLEDGE_STREAK_7", "Seven-day knowledge streak", "SYNTHETIC_MYDATA", 30, "KNOWLEDGE_STREAK_7"));
	private final QuestRepository quests;
	private final QuestInternalRewardRepository rewards;
	private final QuestCompletionRepository completions;
	private final RecordService records;
	private final QuestCommandLock commandLock;
	QuestService(QuestRepository quests, QuestInternalRewardRepository rewards, QuestCompletionRepository completions,
		RecordService records, QuestCommandLock commandLock) {
		this.quests = quests; this.rewards = rewards; this.completions = completions; this.records = records; this.commandLock = commandLock;
	}

	@Transactional
	QuestDtos.QuestPage list(UUID userId) {
		List<Quest> userQuests = quests.findByUserIdOrderByDisplayOrderAsc(userId);
		if (userQuests.isEmpty()) {
			Instant now = Instant.now();
			for (int index = 0; index < SEEDS.size(); index++) {
				QuestSeed seed = SEEDS.get(index);
				Quest quest = quests.save(new Quest(userId, seed.code(), index, seed.title(), seed.verificationKind(), seed.xpReward(), now));
				rewards.save(new QuestInternalReward(quest.getId(), seed.rewardCode()));
			}
			userQuests = quests.findByUserIdOrderByDisplayOrderAsc(userId);
		}
		return new QuestDtos.QuestPage(userQuests.stream().map(this::view).toList(),
			completions.findByUserId(userId).stream().mapToInt(QuestCompletion::getXpAwarded).sum(), "quest-page-v1", "FRESH", null);
	}

	@Transactional
	QuestDtos.QuestView get(UUID userId, UUID questId) {
		list(userId);
		return view(quests.findByIdAndUserId(questId, userId).orElseThrow(QuestNotFoundException::new));
	}

	@Transactional
	CompletionResult complete(UUID userId, UUID questId, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidQuestCommandException("Idempotency-Key must be 16 to 128 characters");
		}
		commandLock.lockUser(userId);
		list(userId);
		Quest quest = quests.findForUpdateByIdAndUserId(questId, userId).orElseThrow(QuestNotFoundException::new);
		QuestCompletion replay = completions.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
		if (replay != null) {
			if (!replay.getQuestId().equals(questId)) throw new QuestIdempotencyKeyConflictException();
			return completionResult(userId, questId, replay.getXpAwarded(), quest.getStatus().equals("DATA_PENDING"));
		}
		QuestCompletion existing = completions.findByQuestId(questId).orElse(null);
		if (existing != null) throw new QuestIdempotencyKeyConflictException();
		if (quest.getVerificationKind().equals("SYNTHETIC_MYDATA")) {
			Instant now = Instant.now();
			quest.markDataPending(now);
			completions.save(new QuestCompletion(userId, questId, idempotencyKey, 0, now));
			return new CompletionResult(new QuestDtos.QuestCompletionView(view(quest), 0, rewardCodes(quest), false), true);
		}
		Instant now = Instant.now();
		quest.markCompleted(now);
		completions.save(new QuestCompletion(userId, questId, idempotencyKey, quest.getXpReward(), now));
		records.appendQuestCompletion(userId, now, quest.getTitle(), quest.getXpReward());
		return new CompletionResult(new QuestDtos.QuestCompletionView(view(quest), quest.getXpReward(), rewardCodes(quest), false), false);
	}

	@Transactional
	public void confirmSyntheticEvidence(UUID userId, Instant evidenceAt) {
		for (Quest quest : quests.findByUserIdOrderByDisplayOrderAsc(userId)) {
			if (quest.getStatus().equals("DATA_PENDING") && quest.getVerificationKind().equals("SYNTHETIC_MYDATA")) {
				quest.markCompleted(evidenceAt);
				QuestCompletion completion = completions.findByQuestId(quest.getId())
					.orElseGet(() -> completions.save(new QuestCompletion(userId, quest.getId(), "synthetic-evidence-" + quest.getId(), 0, evidenceAt)));
				completion.award(quest.getXpReward());
				records.appendQuestCompletion(userId, evidenceAt, quest.getTitle(), quest.getXpReward());
			}
		}
	}

	private QuestDtos.QuestView view(Quest quest) {
		return new QuestDtos.QuestView(quest.getId().toString(), quest.getTitle(), quest.getStatus(), quest.getVerificationKind(),
			quest.getXpReward(), rewardCodes(quest), false, "quest-calc-v1", "FRESH", null);
	}
	private List<String> rewardCodes(Quest quest) {
		return rewards.findByIdQuestIdOrderByIdRewardCodeAsc(quest.getId()).stream().map(QuestInternalReward::getRewardCode).toList();
	}
	private CompletionResult completionResult(UUID userId, UUID questId, int xpAwarded, boolean pending) {
		Quest quest = quests.findByIdAndUserId(questId, userId).orElseThrow(QuestNotFoundException::new);
		return new CompletionResult(new QuestDtos.QuestCompletionView(view(quest), xpAwarded, rewardCodes(quest), false), pending);
	}
	record CompletionResult(QuestDtos.QuestCompletionView body, boolean pending) {
	}

	private record QuestSeed(String code, String title, String verificationKind, int xpReward, String rewardCode) {
	}
}
