package com.gagastudio.finmate.goals;

import java.time.YearMonth;

public record GoalDraft(String title, String domain, long currentAmountKrw, long targetAmountKrw, YearMonth targetMonth) {
}
