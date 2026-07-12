package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.UUID;

record StoredRoutineCommand(UUID userId, String operation, String idempotencyKey, String requestFingerprint,
	int originalStatus, String originalBody, UUID resultBuildId, UUID archivedBuildId, UUID activeBuildId, Instant createdAt) {
}
