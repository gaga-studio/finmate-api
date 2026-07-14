package com.gagastudio.finmate.records;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalDate FIXTURE_TODAY = LocalDate.of(2026, 7, 11);
	private final RecordEventRepository events;
	private final DailyReflectionRepository reflections;

	RecordService(RecordEventRepository events, DailyReflectionRepository reflections) {
		this.events = events;
		this.reflections = reflections;
	}

	RecordDtos.DailyRecordPage records(UUID userId, LocalDate from, LocalDate to) {
		if (to.isBefore(from) || from.plusDays(30).isBefore(to)) throw new InvalidRecordRangeException();
		List<RecordEvent> rangeEvents = events.findByUserIdAndRecordDateBetweenOrderByOccurredAtAsc(userId, from, to);
		List<RecordDtos.DailyRecordView> views = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			LocalDate recordDate = date;
			views.add(record(userId, recordDate,
				rangeEvents.stream().filter(event -> event.getRecordDate().equals(recordDate)).toList()));
		}
		return new RecordDtos.DailyRecordPage(views, "record-page-v2", "FRESH", views.stream()
			.map(RecordDtos.DailyRecordView::lastSyncedAt).filter(java.util.Objects::nonNull)
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
		List<RecordDtos.JourneyNodeView> nodes = new ArrayList<>();
		long incomeKrw = 0;
		long expenseKrw = 0;
		long savingKrw = 0;
		Instant lastSyncedAt = null;
		for (int day = 1; day <= requested.lengthOfMonth(); day++) {
			LocalDate date = requested.atDay(day);
			List<RecordEvent> dayEvents = monthEvents.stream()
				.filter(event -> event.getRecordDate().equals(date)).toList();
			RecordDtos.DailyRecordView record = journeyRecord(userId, date, dayEvents);
			RecordDtos.DailyActivityView primary = record.activities().stream()
				.max(Comparator.comparingLong(this::representativeMagnitude))
				.orElse(plannedActivity(date, "기록 없음"));
			String nodeStatus = requested.equals(YearMonth.of(2026, 7)) && dayEvents.isEmpty()
				&& date.isAfter(FIXTURE_TODAY.plusDays(1))
				? "LOCKED" : record.status();
			nodes.add(new RecordDtos.JourneyNodeView(date.toString(), nodeStatus, primary,
				record.activities().stream().filter(activity -> !activity.activityId().equals(primary.activityId()))
					.map(RecordDtos.DailyActivityView::activityType).distinct().limit(2).toList(),
				Math.max(0, record.activities().size() - 3), !record.activities().isEmpty()));
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
		return new RecordDtos.DailyJourneyMonthView(month,
			(int) nodes.stream().filter(node -> "RECORDED".equals(node.status()) || "TODAY".equals(node.status())).count(),
			requested.lengthOfMonth(), new RecordDtos.MonthlyMoneySummaryView(incomeKrw, expenseKrw, savingKrw), nodes,
			"journey-calc-v1", "FRESH", lastSyncedAt);
	}

	private RecordDtos.DailyRecordView journeyRecord(UUID userId, LocalDate date, List<RecordEvent> dayEvents) {
		if (YearMonth.from(date).equals(YearMonth.of(2026, 7)) && dayEvents.isEmpty()) {
			DailyReflection reflection = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
			return julyJourneyRecord(date, reflection);
		}
		return record(userId, date, dayEvents);
	}

	RecordDtos.DailyRecordView record(UUID userId, LocalDate date) {
		return record(userId, date, events.findByUserIdAndRecordDateOrderByOccurredAtAsc(userId, date));
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

	private RecordDtos.DailyRecordView record(UUID userId, LocalDate date, List<RecordEvent> dayEvents) {
		DailyReflection reflection = reflections.findByIdUserIdAndIdRecordDate(userId, date).orElse(null);
		if (FIXTURE_TODAY.equals(date) && dayEvents.isEmpty()) return julyEleventh(reflection);
		List<RecordDtos.DailyActivityView> activities = dayEvents.stream().map(this::activity).toList();
		Instant lastSyncedAt = dayEvents.stream().map(RecordEvent::getOccurredAt).max(Comparator.naturalOrder())
			.orElse(reflection == null ? null : reflection.getUpdatedAt());
		String status = date.equals(LocalDate.now(SEOUL)) ? "TODAY"
			: activities.isEmpty() && reflection == null ? "EMPTY" : "RECORDED";
		return new RecordDtos.DailyRecordView(date.toString(), status, activities,
			new RecordDtos.BudgetStatusView(0, 0, 0, 0),
			dayEvents.stream().mapToInt(RecordEvent::getXpEarned).sum(),
			reflection == null ? null : reflection.getReflection(),
			dayEvents.stream().anyMatch(event -> "MYDATA_RECALCULATION".equals(event.getEventType()))
				? "금융데이터 재계산 완료" : null,
			"record-calc-v2", "FRESH", lastSyncedAt);
	}

	private RecordDtos.DailyRecordView julyEleventh(DailyReflection reflection) {
		List<RecordDtos.DailyActivityView> activities = List.of(
			activity("activity-salary-0711", "INCOME", "월급 입금", 2_800_000L, "2026-07-11T00:00:00Z", true, "정기수입"),
			activity("activity-expense-0711", "EXPENSE", "지출 3건", -19_600L, "2026-07-11T09:00:00Z", false,
				"식비 12,000원", "카페 4,600원", "교통 3,000원"),
			activity("activity-saving-0711", "SAVING", "비상금 자동저축", 100_000L, "2026-07-11T00:10:00Z", false, "자동저축"),
			activity("activity-investment-0711", "INVESTMENT", "투자계좌 입금", 50_000L, "2026-07-11T00:20:00Z", false, "정기 점검"),
			activity("activity-quest-0711", "QUEST", "카페비 기록 퀘스트 완료", null, "2026-07-11T11:00:00Z", false, "XP +20"));
		return new RecordDtos.DailyRecordView("2026-07-11", "TODAY", activities,
			new RecordDtos.BudgetStatusView(32_000, 19_600, 12_400, 6_125), 20,
			reflection == null ? "주말 외식 전에 예산을 먼저 확인하자." : reflection.getReflection(),
			"저축 데이터 반영 완료", "record-calc-v2", "FRESH", Instant.parse("2026-07-11T12:00:00Z"));
	}

	private RecordDtos.DailyRecordView julyJourneyRecord(LocalDate date, DailyReflection reflection) {
		int day = date.getDayOfMonth();
		List<RecordDtos.DailyActivityView> activities = new ArrayList<>();
		if (day <= 12) activities.add(julyPrimary(date));
		switch (day) {
			case 7 -> {
				activities.add(julySecondary(date, "SAVING", "저축 기록"));
				activities.add(julySecondary(date, "QUEST", "퀘스트 완료"));
			}
			case 8 -> {
				activities.add(julySecondary(date, "INCOME", "수입 기록"));
				activities.add(julySecondary(date, "QUEST", "예산 확인"));
				activities.add(julySecondary(date, "SAVING", "저축 기록"));
			}
			case 9 -> activities.add(julySecondary(date, "QUEST", "생활비 점검"));
			case 10 -> {
				activities.add(julySecondary(date, "EXPENSE", "지출 기록"));
				activities.add(julySecondary(date, "INVESTMENT", "투자 점검"));
			}
			case 11 -> {
				activities.add(julySecondary(date, "SAVING", "저축 기록"));
				activities.add(julySecondary(date, "INVESTMENT", "투자 기록"));
				activities.add(activity("secondary-2026-07-11-expense", "EXPENSE", "지출 3건", -19_600L,
					instant(date, 18), false, "식비", "카페", "교통"));
				activities.add(julySecondary(date, "QUEST", "퀘스트 완료"));
			}
			default -> { }
		}
		String status = day <= 10 ? "RECORDED" : day == 11 ? "TODAY" : day == 12 ? "PLANNED" : "EMPTY";
		Instant lastSyncedAt = day <= 11 && !activities.isEmpty()
			? activities.stream().map(RecordDtos.DailyActivityView::occurredAt).max(Comparator.naturalOrder()).orElse(null)
			: reflection == null ? null : reflection.getUpdatedAt();
		return new RecordDtos.DailyRecordView(date.toString(), status, activities,
			new RecordDtos.BudgetStatusView(0, 0, 0, 0), 0,
			reflection == null ? null : reflection.getReflection(), null,
			"record-calc-v2", "FRESH", lastSyncedAt);
	}

	private RecordDtos.DailyActivityView julySecondary(LocalDate date, String type, String title) {
		return activity("secondary-%s-%s".formatted(date, type.toLowerCase()), type, title, null,
			instant(date, 20), false, title);
	}

	private RecordDtos.DailyActivityView julyPrimary(LocalDate date) {
		return switch (date.getDayOfMonth()) {
			case 1 -> activity("a01", "EXPENSE", "생활비 결제", -18_500L, instant(date, 18), true, "생활");
			case 2 -> activity("a02", "EXPENSE", "교통비", -6_200L, instant(date, 18), true, "교통");
			case 3 -> activity("a03", "QUEST", "예산 확인 완료", null, instant(date, 20), true, "XP +10");
			case 4 -> activity("a04", "EXPENSE", "식비", -21_000L, instant(date, 19), true, "식비");
			case 5 -> activity("a05", "EXPENSE", "주말 지출", -32_700L, instant(date, 19), true, "외식");
			case 6 -> activity("a06", "QUEST", "무지출 기록", null, instant(date, 21), true, "완료");
			case 7 -> activity("a07", "EXPENSE", "식비·교통", -24_500L, instant(date, 18), true, "식비", "교통");
			case 8 -> activity("a08", "EXPENSE", "카페비", -8_900L, instant(date, 17), true, "카페");
			case 9 -> activity("a09", "EXPENSE", "장보기", -32_000L, instant(date, 19), true, "생활");
			case 10 -> activity("a10", "SAVING", "비상금 자동저축", 100_000L, instant(date, 9), true, "자동저축");
			case 11 -> activity("a11", "INCOME", "월급 입금", 2_800_000L, instant(date, 9), true, "정기수입");
			case 12 -> activity("a12", "QUEST", "오늘 예산 28,000원", null, instant(date, 9), true, "예정 2개");
			default -> activity("a%02d".formatted(date.getDayOfMonth()), "QUEST", "기록 예정", null,
				instant(date, 9), true, "잠김");
		};
	}

	private RecordDtos.DailyActivityView activity(RecordEvent event) {
		String type = "MYDATA_RECALCULATION".equals(event.getEventType()) ? "MYDATA_RECALCULATION" : event.getEventType();
		return new RecordDtos.DailyActivityView(event.getId().toString(), type, event.getTitle(), event.getAmountKrw(),
			event.getOccurredAt(), true, event.getXpEarned() > 0 ? List.of("XP +" + event.getXpEarned()) : List.of());
	}

	private long representativeMagnitude(RecordDtos.DailyActivityView activity) {
		return activity.amountKrw() == null ? 0 : Math.abs(activity.amountKrw());
	}

	private RecordDtos.DailyActivityView plannedActivity(LocalDate date, String title) {
		return activity("empty-" + date, "QUEST", title, null, instant(date, 9), true, "기록 없음");
	}

	private RecordDtos.DailyActivityView activity(String id, String type, String title, Long amount,
		String occurredAt, boolean primary, String... labels) {
		return activity(id, type, title, amount, Instant.parse(occurredAt), primary, labels);
	}

	private RecordDtos.DailyActivityView activity(String id, String type, String title, Long amount,
		Instant occurredAt, boolean primary, String... labels) {
		return new RecordDtos.DailyActivityView(id, type, title, amount, occurredAt, primary, List.of(labels));
	}

	private Instant instant(LocalDate date, int hourKst) {
		return date.atTime(hourKst, 0).atZone(SEOUL).toInstant();
	}
}
