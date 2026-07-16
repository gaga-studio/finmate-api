package com.gagastudio.finmate.runtime;

import java.time.LocalDate;

public record RuntimeBehaviorProfile(
	boolean riskProfileChecked,
	boolean diversificationChecked,
	boolean investmentLearningCompleted,
	int investmentJudgmentBps,
	int questXp,
	LocalDate lastEvidenceDate
) {
}
