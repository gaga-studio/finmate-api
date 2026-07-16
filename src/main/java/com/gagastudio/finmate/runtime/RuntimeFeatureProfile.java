package com.gagastudio.finmate.runtime;

import java.time.LocalDate;

public record RuntimeFeatureProfile(
	LocalDate featureMonth,
	Integer consumptionRateBps,
	Integer savingRateBps,
	Integer investmentRateBps,
	Integer defenseScoreBps,
	Integer savingScoreBps,
	Integer investmentScoreBps
) {
}
