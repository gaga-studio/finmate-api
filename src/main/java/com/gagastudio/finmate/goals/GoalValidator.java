package com.gagastudio.finmate.goals;

import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class GoalValidator {
	public void validate(GoalDraft goal, boolean confirmMainGoal) {
		if (!confirmMainGoal) {
			throw new InvalidMainGoalException("Main goal confirmation is required");
		}
		if (goal.currentAmountKrw() < 0 || goal.targetAmountKrw() <= 0 || goal.currentAmountKrw() >= goal.targetAmountKrw()) {
			throw new InvalidMainGoalException("Current amount must be less than target amount");
		}
		if (goal.targetMonth().isBefore(YearMonth.now())) {
			throw new InvalidMainGoalException("Target month must not be in the past");
		}
	}
}
