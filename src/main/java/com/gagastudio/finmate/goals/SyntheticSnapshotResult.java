package com.gagastudio.finmate.goals;

import java.time.Instant;
import java.util.UUID;

public record SyntheticSnapshotResult(UUID goalId, UUID raidId, long currentAmountKrw, int currentProgressBps,
	int highestProgressBps, int stage, int bossHpBps, Instant lastSyncedAt) {
}
