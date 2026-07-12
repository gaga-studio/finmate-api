package com.gagastudio.finmate.records;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_daily_reflection")
class DailyReflection {
	@EmbeddedId private DailyReflectionId id;
	@Column(nullable = false) private String reflection;
	@Column(name = "updated_at", nullable = false) private Instant updatedAt;
	protected DailyReflection() {
	}
	DailyReflection(UUID userId, LocalDate date, String reflection, Instant updatedAt) {
		this.id = new DailyReflectionId(userId, date); this.reflection = reflection; this.updatedAt = updatedAt;
	}
	void update(String value, Instant at) { reflection = value; updatedAt = at; }
	String getReflection() { return reflection; }
	Instant getUpdatedAt() { return updatedAt; }
	@Embeddable
	static class DailyReflectionId implements Serializable {
		@Column(name = "user_id") private UUID userId;
		@Column(name = "record_date") private LocalDate recordDate;
		protected DailyReflectionId() {
		}
		DailyReflectionId(UUID userId, LocalDate recordDate) { this.userId = userId; this.recordDate = recordDate; }
		@Override public boolean equals(Object other) {
			return other instanceof DailyReflectionId that && Objects.equals(userId, that.userId) && Objects.equals(recordDate, that.recordDate);
		}
		@Override public int hashCode() { return Objects.hash(userId, recordDate); }
	}
}
