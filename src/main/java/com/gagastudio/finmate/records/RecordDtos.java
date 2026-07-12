package com.gagastudio.finmate.records;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class RecordDtos {
	private RecordDtos() {
	}
	record RecordEventView(String eventType, Instant occurredAt, String title) {
	}
	record DailyRecordView(String date, List<RecordEventView> events, int xpEarned, String reflection,
		String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
	record DailyRecordPage(List<DailyRecordView> items, String calculationVersion, String dataState, Instant lastSyncedAt) {
	}
	record SaveReflectionRequest(@NotBlank @Size(max = 500) String reflection) {
	}
}
