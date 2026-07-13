package com.gagastudio.finmate.rewards;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_user_cosmetic")
class UserCosmetic {
	@EmbeddedId
	private UserCosmeticId id;
	@Column(name = "acquired_at", nullable = false)
	private Instant acquiredAt;

	protected UserCosmetic() {
	}

	UserCosmetic(UUID userId, String cosmeticId, Instant acquiredAt) {
		this.id = new UserCosmeticId(userId, cosmeticId);
		this.acquiredAt = acquiredAt;
	}

	@Embeddable
	static class UserCosmeticId implements Serializable {
		@Column(name = "user_id")
		private UUID userId;
		@Column(name = "cosmetic_id")
		private String cosmeticId;

		protected UserCosmeticId() {
		}

		UserCosmeticId(UUID userId, String cosmeticId) {
			this.userId = userId;
			this.cosmeticId = cosmeticId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof UserCosmeticId that && Objects.equals(userId, that.userId)
				&& Objects.equals(cosmeticId, that.cosmeticId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, cosmeticId);
		}
	}
}
