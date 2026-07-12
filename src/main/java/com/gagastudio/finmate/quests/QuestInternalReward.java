package com.gagastudio.finmate.quests;

import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_quest_internal_reward")
class QuestInternalReward {
	@EmbeddedId private QuestInternalRewardId id;
	protected QuestInternalReward() {
	}
	QuestInternalReward(UUID questId, String rewardCode) { id = new QuestInternalRewardId(questId, rewardCode); }
	String getRewardCode() { return id.rewardCode; }
	@Embeddable
	static class QuestInternalRewardId implements Serializable {
		@Column(name = "quest_id") private UUID questId;
		@Column(name = "reward_code") private String rewardCode;
		protected QuestInternalRewardId() {
		}
		QuestInternalRewardId(UUID questId, String rewardCode) { this.questId = questId; this.rewardCode = rewardCode; }
		@Override public boolean equals(Object other) {
			return other instanceof QuestInternalRewardId that && Objects.equals(questId, that.questId) && Objects.equals(rewardCode, that.rewardCode);
		}
		@Override public int hashCode() { return Objects.hash(questId, rewardCode); }
	}
}
