package com.gagastudio.finmate.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 적재 전용 형태. JPA 엔티티가 아니다.
 *
 * 처음엔 엔티티를 그대로 JDBC 배치에 넘겼는데, 엔티티에는 조회에 필요한 getter만 있어서
 * 적재기가 리플렉션으로 private 필드를 읽고 있었다. 그건 "이 두 관심사가 같은 타입을 쓰면 안 된다"는
 * 신호였다.
 *
 * 적재는 파일의 한 줄을 그대로 한 행으로 옮기는 일이고 도메인 규칙이 없다.
 * 반면 엔티티는 조회에 쓰이며 필요한 것만 노출한다. 그래서 타입을 갈랐다.
 */
final class BundleRows {

	private BundleRows() {
	}

	record PersonaRow(
		UUID id, String externalId, String displayName,
		short age, String cohort, String job, String archetype, String region,
		String householdType, short householdSize,
		long monthlyIncome, String incomeBand, String incomeRegularity, long targetMonthlySpend,
		BigDecimal targetSavingRate, BigDecimal targetInvestmentRate, boolean investParticipation,
		short riskScore, String riskAttitude,
		LocalDate dataFrom, LocalDate dataTo) {
	}

	record LedgerRow(
		UUID personaId, String externalId, LocalDateTime occurredAt, String merchant, long amount,
		String category, String flow, String major, String minor,
		String ruleId, String paymentMethod, String memo) {
	}

	record Bundle(PersonaRow persona, List<LedgerRow> entries) {
	}
}
