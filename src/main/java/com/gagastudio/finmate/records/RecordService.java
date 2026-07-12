package com.gagastudio.finmate.records;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordService {
	private final RecordEventRepository events;
	private final DailyReflectionRepository reflections;
	RecordService(RecordEventRepository events, DailyReflectionRepository reflections) { this.events = events; this.reflections = reflections; }
	RecordDtos.DailyRecordPage records(UUID userId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || from.plusDays(30).isBefore(to)) throw new InvalidRecordRangeException();
		List<RecordEvent> rangeEvents = events.findByUserIdAndRecordDateBetweenOrderByOccurredAtAsc(userId, from, to);
		List<RecordDtos.DailyRecordView> views = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			LocalDate recordDate = date;
			views.add(record(userId, recordDate, rangeEvents.stream().filter(event -> event.getRecordDate().equals(recordDate)).toList()));
		}
		return new RecordDtos.DailyRecordPage(views, "record-page-v1", "FRESH", views.stream()
			.map(RecordDtos.DailyRecordView::lastSyncedAt).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
	}
	RecordDtos.DailyRecordView record(UUID userId, LocalDate date) { return record(userId, date, events.findByUserIdAndRecordDateOrderByOccurredAtAsc(userId, date)); }
	@Transactional
	RecordDtos.DailyRecordView saveReflection(UUID userId, LocalDate date, String reflection) {
		Instant now = Instant.now();
		DailyReflection existing = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
		if (existing == null) reflections.save(new DailyReflection(userId, date, reflection.trim(), now)); else existing.update(reflection.trim(), now);
		return record(userId, date);
	}
	@Transactional
	public void appendQuestCompletion(UUID userId, Instant occurredAt, String title, int xpEarned) {
		events.save(new RecordEvent(userId, occurredAt, "QUEST", title, xpEarned));
	}
	@Transactional
	public void appendSyntheticRecalculation(UUID userId, Instant occurredAt) {
		events.save(new RecordEvent(userId, occurredAt, "MYDATA_RECALCULATION", "Synthetic MyData recalculated", 0));
	}
	private RecordDtos.DailyRecordView record(UUID userId, LocalDate date, List<RecordEvent> dayEvents) {
		DailyReflection reflection = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
		List<RecordDtos.RecordEventView> events = dayEvents.stream()
			.map(event -> new RecordDtos.RecordEventView(event.getEventType(), event.getOccurredAt(), event.getTitle())).toList();
		Instant lastSyncedAt = dayEvents.stream().map(RecordEvent::getOccurredAt).max(Comparator.naturalOrder())
			.orElse(reflection == null ? null : reflection.getUpdatedAt());
		return new RecordDtos.DailyRecordView(date.toString(), events, dayEvents.stream().mapToInt(RecordEvent::getXpEarned).sum(),
			reflection == null ? null : reflection.getReflection(), "record-calc-v1", "FRESH", lastSyncedAt);
	}
}
