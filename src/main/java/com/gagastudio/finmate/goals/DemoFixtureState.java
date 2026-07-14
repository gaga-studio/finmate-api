package com.gagastudio.finmate.goals;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_demo_fixture_state")
class DemoFixtureState {
	@EmbeddedId private DemoFixtureStateId id;
	@Column(name = "stage", nullable = false) private int nextFrameIndex;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;
	protected DemoFixtureState() {
	}
	DemoFixtureState(UUID userId, String fixtureId, Instant updatedAt) {
		this.id = new DemoFixtureStateId(userId, fixtureId); this.nextFrameIndex = 0; this.updatedAt = updatedAt;
	}
	int getNextFrameIndex() { return nextFrameIndex; }
	void advanceTo(int nextFrameIndex, Instant at) { this.nextFrameIndex = nextFrameIndex; this.updatedAt = at; }
	@Embeddable
	static class DemoFixtureStateId implements Serializable {
		@Column(name = "user_id") private UUID userId;
		@Column(name = "fixture_id") private String fixtureId;
		protected DemoFixtureStateId() {
		}
		DemoFixtureStateId(UUID userId, String fixtureId) { this.userId = userId; this.fixtureId = fixtureId; }
		@Override public boolean equals(Object other) {
			return other instanceof DemoFixtureStateId that && Objects.equals(userId, that.userId) && Objects.equals(fixtureId, that.fixtureId);
		}
		@Override public int hashCode() { return Objects.hash(userId, fixtureId); }
	}
}
