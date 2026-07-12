package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finmate_routine_build")
class RoutineBuild {
	@Id
	private UUID id;
	@Column(name = "user_id", nullable = false)
	private UUID userId;
	@Column(name = "candidate_id", nullable = false)
	private String candidateId;
	@Column(name = "source_routine_id", nullable = false)
	private String sourceRoutineId;
	@Column(nullable = false)
	private String domain;
	@Column(nullable = false)
	private String difficulty;
	@Column(nullable = false)
	private String status;
	@Column(nullable = false)
	private String steps;
	@Column(name = "activated_at", nullable = false)
	private Instant activatedAt;
	@Column(name = "archived_at")
	private Instant archivedAt;
	@Column(name = "replaces_build_id")
	private UUID replacesBuildId;
	@Column(name = "replaced_by_build_id")
	private UUID replacedByBuildId;
	@Column(name = "command_type", nullable = false)
	private String commandType;
	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;
	@Column(name = "calculation_version", nullable = false)
	private String calculationVersion;
	@Column(name = "data_state", nullable = false)
	private String dataState;
	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;

	protected RoutineBuild() {
	}

	RoutineBuild(UUID userId, RoutineAdaptation adaptation, RoutineCandidate candidate, Instant now,
		UUID replacesBuildId, String commandType, String idempotencyKey) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.candidateId = candidate.candidateId();
		this.sourceRoutineId = adaptation.getSourceRoutineId();
		this.domain = candidate.domain();
		this.difficulty = candidate.difficulty();
		this.status = "ACTIVE";
		this.steps = String.join("\n", candidate.steps());
		this.activatedAt = now;
		this.replacesBuildId = replacesBuildId;
		this.commandType = commandType;
		this.idempotencyKey = idempotencyKey;
		this.calculationVersion = "build-calc-v1";
		this.dataState = "FRESH";
		this.lastSyncedAt = now;
	}

	void archive(Instant now) {
		this.status = "ARCHIVED";
		this.archivedAt = now;
		this.lastSyncedAt = now;
	}

	void linkReplacement(UUID replacementId, Instant now) {
		this.replacedByBuildId = replacementId;
		this.lastSyncedAt = now;
	}

	UUID getId() { return id; }
	UUID getUserId() { return userId; }
	String getCandidateId() { return candidateId; }
	String getSourceRoutineId() { return sourceRoutineId; }
	String getDomain() { return domain; }
	String getDifficulty() { return difficulty; }
	String getStatus() { return status; }
	List<String> steps() { return List.of(steps.split("\\n")); }
	Instant getActivatedAt() { return activatedAt; }
	Instant getArchivedAt() { return archivedAt; }
	UUID getReplacesBuildId() { return replacesBuildId; }
	UUID getReplacedByBuildId() { return replacedByBuildId; }
	Instant getLastSyncedAt() { return lastSyncedAt; }
}
