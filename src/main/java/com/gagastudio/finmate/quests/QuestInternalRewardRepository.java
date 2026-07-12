package com.gagastudio.finmate.quests;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestInternalRewardRepository extends JpaRepository<QuestInternalReward, QuestInternalReward.QuestInternalRewardId> {
	List<QuestInternalReward> findByIdQuestIdOrderByIdRewardCodeAsc(UUID questId);
}
