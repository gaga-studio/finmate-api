package com.gagastudio.finmate.goals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class GoalRulesTest {
	private final GoalValidator validator = new GoalValidator();

	@Test
	void rejectsAnUnconfirmedMainGoal() {
		assertThatThrownBy(() -> validator.validate(draft(2_000_000, 5_000_000, YearMonth.now().plusMonths(1)), false))
			.isInstanceOf(InvalidMainGoalException.class);
	}

	@Test
	void rejectsGoalWhoseCurrentAmountIsAtLeastTheTarget() {
		assertThatThrownBy(() -> validator.validate(draft(5_000_000, 5_000_000, YearMonth.now().plusMonths(1)), true))
			.isInstanceOf(InvalidMainGoalException.class);
	}

	@Test
	void rejectsGoalWhoseTargetMonthIsInThePast() {
		assertThatThrownBy(() -> validator.validate(draft(2_000_000, 5_000_000, YearMonth.now().minusMonths(1)), true))
			.isInstanceOf(InvalidMainGoalException.class);
	}

	@Test
	void normalizesProgressAgainstTheConfirmedFinancialBaseline() {
		assertThat(GoalProgress.normalizedBps(2_000_000, 5_000_000, 2_000_000)).isZero();
		assertThat(GoalProgress.normalizedBps(2_000_000, 5_000_000, 3_500_000)).isEqualTo(5_000);
		assertThat(GoalProgress.normalizedBps(2_000_000, 5_000_000, 6_000_000)).isEqualTo(10_000);
	}

	@Test
	void mapsEveryRaidStageBoundary() {
		assertThat(GoalProgress.stageForHighestProgress(3_299)).isEqualTo(1);
		assertThat(GoalProgress.stageForHighestProgress(3_300)).isEqualTo(2);
		assertThat(GoalProgress.stageForHighestProgress(6_599)).isEqualTo(2);
		assertThat(GoalProgress.stageForHighestProgress(6_600)).isEqualTo(3);
		assertThat(GoalProgress.stageForHighestProgress(10_000)).isEqualTo(3);
	}

	@Test
	void calculatesBossHpWithinTheUnlockedStage() {
		assertThat(GoalProgress.bossHpBpsForHighestProgress(0)).isEqualTo(10_000);
		assertThat(GoalProgress.bossHpBpsForHighestProgress(3_299)).isEqualTo(4);
		assertThat(GoalProgress.bossHpBpsForHighestProgress(3_300)).isEqualTo(10_000);
		assertThat(GoalProgress.bossHpBpsForHighestProgress(6_599)).isEqualTo(4);
		assertThat(GoalProgress.bossHpBpsForHighestProgress(6_600)).isEqualTo(10_000);
		assertThat(GoalProgress.bossHpBpsForHighestProgress(10_000)).isZero();
	}

	private GoalDraft draft(long currentAmountKrw, long targetAmountKrw, YearMonth targetMonth) {
		return new GoalDraft("Europe travel", "SAVING", currentAmountKrw, targetAmountKrw, targetMonth);
	}
}
