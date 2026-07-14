package com.gagastudio.finmate.records;

import com.gagastudio.finmate.runtime.RuntimeFinancialActivity;
import com.gagastudio.finmate.runtime.SyntheticRuntimeReadService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final RecordEventRepository events;
	private final DailyReflectionRepository reflections;
	private final SyntheticRuntimeReadService runtimeReads;

	RecordService(RecordEventRepository events, DailyReflectionRepository reflections,
		SyntheticRuntimeReadService runtimeReads) {
		this.events = events;
		this.reflections = reflections;
		this.runtimeReads = runtimeReads;
	}

	RecordDtos.DailyRecordPage records(UUID userId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || from.plusDays(30).isBefore(to)) throw new InvalidRecordRangeException();
		List<RecordEvent> rangeEvents = events.findByUserIdAndRecordDateBetweenOrderByOccurredAtAsc(userId, from, to);
		List<RuntimeFinancialActivity> financialActivities = runtimeReads.activities(userId, from, to);
		List<RecordDtos.DailyRecordView> views = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			views.add(record(userId, date, eventsOn(date, rangeEvents), activitiesOn(date, financialActivities)));
		}
		return new RecordDtos.DailyRecordPage(views, "record-page-v2", dataState(views), views.stream()
			.map(RecordDtos.DailyRecordView::lastSyncedAt).filter(Objects::nonNull)
			.max(Comparator.naturalOrder()).orElse(null));
	}

	RecordDtos.DailyJourneyMonthView journey(UUID userId, String month) {
		YearMonth requested;
		try {
			requested = YearMonth.parse(month);
		} catch (RuntimeException exception) {
			throw new InvalidRecordRangeException();
		}
		List<RecordEvent> monthEvents = events.findByUserIdAndRecordDateBetweenOrderByOccurredAtAsc(userId,
			requested.atDay(1), requested.atEndOfMonth());
		List<RuntimeFinancialActivity> financialActivities = runtimeReads.activities(userId,
			requested.atDay(1), requested.atEndOfMonth());
		List<RecordDtos.JourneyNodeView> nodes = new ArrayList<>();
		long incomeKrw = 0;
		long expenseKrw = 0;
		long savingKrw = 0;
		Instant lastSyncedAt = null;
		for (int day = 1; day <= requested.lengthOfMonth(); day++) {
			LocalDate date = requested.atDay(day);
			RecordDtos.DailyRecordView record = record(userId, date, eventsOn(date, monthEvents),
				activitiesOn(date, financialActivities));
			RecordDtos.DailyActivityView primary = record.activities().stream()
				.filter(RecordDtos.DailyActivityView::primary).findFirst().orElse(null);
			List<String> secondaryTypes = record.activities().stream()
				.filter(activity -> primary == null || !activity.activityId().equals(primary.activityId()))
				.map(RecordDtos.DailyActivityView::activityType).distinct().limit(2).toList();
			nodes.add(new RecordDtos.JourneyNodeView(date.toString(), journeyStatus(date, record), primary,
				secondaryTypes, Math.max(0, record.activities().size() - 3), !record.activities().isEmpty()
					|| record.reflection() != null));
			for (RecordDtos.DailyActivityView activity : record.activities()) {
				if (activity.amountKrw() == null) continue;
				switch (activity.activityType()) {
					case "INCOME" -> incomeKrw += Math.max(0, activity.amountKrw());
					case "EXPENSE" -> expenseKrw += Math.abs(activity.amountKrw());
					case "SAVING" -> savingKrw += Math.max(0, activity.amountKrw());
					default -> { }
				}
			}
			if (record.lastSyncedAt() != null && (lastSyncedAt == null || record.lastSyncedAt().isAfter(lastSyncedAt))) {
				lastSyncedAt = record.lastSyncedAt();
			}
		}
		int recordedDayCount = (int) nodes.stream().filter(RecordDtos.JourneyNodeView::detailAvailable).count();
		String dataState = recordedDayCount == 0 ? "INSUFFICIENT" : "FRESH";
		return new RecordDtos.DailyJourneyMonthView(month, recordedDayCount, requested.lengthOfMonth(),
			new RecordDtos.MonthlyMoneySummaryView(incomeKrw, expenseKrw, savingKrw), nodes,
			"journey-calc-v1", dataState, lastSyncedAt);
	}

	RecordDtos.DailyRecordView record(UUID userId, LocalDate date) {
		return record(userId, date, events.findByUserIdAndRecordDateOrderByOccurredAtAsc(userId, date),
			runtimeReads.activities(userId, date, date));
	}

	@Transactional
	RecordDtos.DailyRecordView saveReflection(UUID userId, LocalDate date, String reflection) {
		Instant now = Instant.now();
		DailyReflection existing = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
		if (existing == null) reflections.save(new DailyReflection(userId, date, reflection.trim(), now));
		else existing.update(reflection.trim(), now);
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

	@Transactional
	public void appendDemoSaving(UUID userId, Instant occurredAt, long amountKrw) {
		events.save(new RecordEvent(userId, occurredAt, "SAVING", "자동저축 반영", 0, amountKrw));
	}

	private RecordDtos.DailyRecordView record(UUID userId, LocalDate date, List<RecordEvent> dayEvents,
		List<RuntimeFinancialActivity> financialActivities) {
		DailyReflection reflection = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
		List<RecordDtos.DailyActivityView> activities = new ArrayList<>();
		financialActivities.stream().map(this::activity).forEach(activities::add);
		dayEvents.stream().map(this::activity).forEach(activities::add);
		activities.sort(Comparator.comparing(RecordDtos.DailyActivityView::occurredAt)
			.thenComparing(RecordDtos.DailyActivityView::activityId));
		activities = markPrimary(activities);
		Instant lastSyncedAt = activities.stream().map(RecordDtos.DailyActivityView::occurredAt)
			.max(Comparator.naturalOrder()).orElse(reflection == null ? null : reflection.getUpdatedAt());
		if (reflection != null && (lastSyncedAt == null || reflection.getUpdatedAt().isAfter(lastSyncedAt))) {
			lastSyncedAt = reflection.getUpdatedAt();
		}
		boolean hasData = !activities.isEmpty() || reflection != null;
		String status = hasData ? (date.equals(LocalDate.now(SEOUL)) ? "TODAY" : "RECORDED")
			: date.equals(LocalDate.now(SEOUL)) ? "TODAY" : "EMPTY";
		return new RecordDtos.DailyRecordView(date.toString(), status, activities,
			new RecordDtos.BudgetStatusView(0, 0, 0, 0),
			dayEvents.stream().mapToInt(RecordEvent::getXpEarned).sum(),
			reflection == null ? null : reflection.getReflection(),
			dayEvents.stream().anyMatch(event -> "MYDATA_RECALCULATION".equals(event.getEventType()))
				? "금융데이터 재계산 완료" : null,
			"record-calc-v2", hasData ? "FRESH" : "INSUFFICIENT", lastSyncedAt);
	}

	private List<RecordDtos.DailyActivityView> markPrimary(List<RecordDtos.DailyActivityView> activities) {
		RecordDtos.DailyActivityView primary = activities.stream()
			.max(Comparator.comparingLong(this::representativeMagnitude)).orElse(null);
		if (primary == null) return List.of();
		return activities.stream().map(activity -> new RecordDtos.DailyActivityView(
			activity.activityId(), activity.activityType(), activity.title(), activity.amountKrw(), activity.occurredAt(),
			activity.activityId().equals(primary.activityId()), activity.categoryLabels())).toList();
	}

	private RecordDtos.DailyActivityView activity(RuntimeFinancialActivity activity) {
		String type = switch (activity.activityType()) {
			case "SPENDING" -> "EXPENSE";
			case "INCOME", "SAVING", "INVESTMENT" -> activity.activityType();
			default -> throw new IllegalStateException("Unsupported financial activity type: " + activity.activityType());
		};
		long amount = switch (type) {
			case "EXPENSE" -> "OUTFLOW".equals(activity.direction()) ? -activity.amountKrw() : activity.amountKrw();
			case "SAVING", "INVESTMENT" -> "OUTFLOW".equals(activity.direction())
				? activity.amountKrw() : -activity.amountKrw();
			default -> "INFLOW".equals(activity.direction()) ? activity.amountKrw() : -activity.amountKrw();
		};
		LinkedHashSet<String> labels = new LinkedHashSet<>();
		labels.add(activity.category());
		labels.add(activity.subcategory());
		labels.removeIf(value -> value == null || value.isBlank());
		return new RecordDtos.DailyActivityView(activity.sourceTransactionId(), type, activity.displayLabel(), amount,
			activity.occurredAt(), false, labels.stream().limit(3).toList());
	}

	private RecordDtos.DailyActivityView activity(RecordEvent event) {
		return new RecordDtos.DailyActivityView(event.getId().toString(), event.getEventType(), event.getTitle(),
			event.getAmountKrw(), event.getOccurredAt(), false,
			event.getXpEarned() > 0 ? List.of("XP +" + event.getXpEarned()) : List.of());
	}

	private List<RecordEvent> eventsOn(LocalDate date, List<RecordEvent> source) {
		return source.stream().filter(event -> event.getRecordDate().equals(date)).toList();
	}

	private List<RuntimeFinancialActivity> activitiesOn(LocalDate date, List<RuntimeFinancialActivity> source) {
		return source.stream().filter(activity -> activity.occurredAt().atZone(SEOUL).toLocalDate().equals(date)).toList();
	}

	private long representativeMagnitude(RecordDtos.DailyActivityView activity) {
		return activity.amountKrw() == null ? 0 : Math.abs(activity.amountKrw());
	}

	private String journeyStatus(LocalDate date, RecordDtos.DailyRecordView record) {
		if (!record.activities().isEmpty() || record.reflection() != null) {
			return date.equals(LocalDate.now(SEOUL)) ? "TODAY" : "RECORDED";
		}
		if (date.equals(LocalDate.now(SEOUL))) return "TODAY";
		return date.isAfter(LocalDate.now(SEOUL)) ? "LOCKED" : "EMPTY";
	}

	private String dataState(List<RecordDtos.DailyRecordView> views) {
		return views.stream().anyMatch(view -> "FRESH".equals(view.dataState())) ? "FRESH" : "INSUFFICIENT";
	}
}
