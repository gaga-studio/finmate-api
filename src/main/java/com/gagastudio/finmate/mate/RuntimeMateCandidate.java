package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.time.LocalDate;

record RuntimeMateCandidate(String sourcePersonaId, String adventurerId, String ageBand, String occupationGroup,
	String incomeBand, String spendingTendency, String savingRateBand, String investmentTendency,
	String householdType, String lifestyleTags, Instant lastSyncedAt, LocalDate dataAsOf,
	String sourceGroupId, String routineId, String routineDomain, String routineFrequency, int maintainedMonths) {
}
