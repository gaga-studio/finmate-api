package com.gagastudio.finmate.metrics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 화면의 일간·주간·월간 축.
 *
 * 앱의 정의를 그대로 따른다 — 일간은 그날 하루, 주간은 그 주 일요일부터, 월간은 그달 1일부터,
 * 셋 다 기준일까지다. 즉 주간·월간은 "지금까지 얼마나 썼는가"이지 한 주/한 달 전체가 아니다.
 * 진행 중인 기간의 예산을 보여주는 화면이라 그게 맞다.
 */
public enum PeriodType {
	DAILY,
	WEEKLY,
	MONTHLY;

	public record Range(LocalDate start, LocalDate end) {
	}

	public Range range(LocalDate referenceDate) {
		return switch (this) {
			case DAILY -> new Range(referenceDate, referenceDate);
			// 앱이 일요일 시작이다 (Date.getDay()가 0=일요일)
			case WEEKLY -> new Range(
				referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)), referenceDate);
			case MONTHLY -> new Range(referenceDate.withDayOfMonth(1), referenceDate);
		};
	}

	/**
	 * 이 기간의 지출 한도.
	 *
	 * 앱은 일 2만 / 주 25만 / 월 65만을 상수로 박아 두었다. 사람마다 소득이 다른데 한도가 같을 수는
	 * 없어서, 그 사람의 월 목표 지출에서 나눠 쓴다. 주는 4주, 일은 30일로 나눈다 —
	 * 달마다 28~31일로 흔들리면 "어제보다 예산이 늘었다"가 되어 화면이 이상해진다.
	 */
	public long budgetLimit(long targetMonthlySpend) {
		return switch (this) {
			case DAILY -> Math.round(targetMonthlySpend / 30.0);
			case WEEKLY -> Math.round(targetMonthlySpend / 4.0);
			case MONTHLY -> targetMonthlySpend;
		};
	}

	public static PeriodType from(String raw) {
		return switch (raw == null ? "" : raw.toLowerCase()) {
			case "daily" -> DAILY;
			case "weekly" -> WEEKLY;
			case "monthly" -> MONTHLY;
			default -> throw new IllegalArgumentException("기간은 daily · weekly · monthly 중 하나여야 합니다: " + raw);
		};
	}

	public String wireName() {
		return name().toLowerCase();
	}
}
