package com.gagastudio.finmate.runtime;

import java.time.Instant;

public record RuntimeSavingDelta(long netInflowKrw, Instant lastSyncedAt) {
}
