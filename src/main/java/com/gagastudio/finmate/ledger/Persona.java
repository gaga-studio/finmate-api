package com.gagastudio.finmate.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 합성 인구 한 명. 또래 비교의 모집단이다.
 *
 * 로그인하는 사람({@code finmate_user})과 분리했다. 2,000명이 전부 로그인 계정일 필요가 없고,
 * 그렇게 만들면 쓰지도 않을 비밀번호 해시가 2,000개 생기면서 인증 테이블이 인구 통계 조회에
 * 끌려 들어온다.
 *
 * 실존 인물이 아니다. gaga-studio/finmate-data가 결정적 시드로 생성한 합성 데이터다.
 */
@Entity
@Table(name = "persona")
public class Persona {
	@Id
	private UUID id;

	@Column(name = "external_id", nullable = false, unique = true, length = 16)
	private String externalId;

	@Column(name = "display_name", nullable = false, length = 40)
	private String displayName;

	@Column(nullable = false)
	private short age;

	@Column(nullable = false, length = 8)
	private String cohort;

	@Column(nullable = false, length = 40)
	private String job;

	@Column(nullable = false, length = 40)
	private String archetype;

	@Column(nullable = false, length = 40)
	private String region;

	@Column(name = "household_type", nullable = false, length = 40)
	private String householdType;

	@Column(name = "household_size", nullable = false)
	private short householdSize;

	@Column(name = "monthly_income", nullable = false)
	private long monthlyIncome;

	@Column(name = "income_band", nullable = false, length = 40)
	private String incomeBand;

	@Column(name = "income_regularity", nullable = false, length = 16)
	private String incomeRegularity;

	@Column(name = "target_monthly_spend", nullable = false)
	private long targetMonthlySpend;

	@Column(name = "target_saving_rate", nullable = false, precision = 5, scale = 4)
	private BigDecimal targetSavingRate;

	@Column(name = "target_investment_rate", nullable = false, precision = 5, scale = 4)
	private BigDecimal targetInvestmentRate;

	@Column(name = "invest_participation", nullable = false)
	private boolean investParticipation;

	@Column(name = "risk_score", nullable = false)
	private short riskScore;

	@Column(name = "risk_attitude", nullable = false, length = 24)
	private String riskAttitude;

	@Column(name = "data_from", nullable = false)
	private LocalDate dataFrom;

	@Column(name = "data_to", nullable = false)
	private LocalDate dataTo;

	protected Persona() {
	}

	public Persona(UUID id, String externalId, String displayName) {
		this.id = id;
		this.externalId = externalId;
		this.displayName = displayName;
	}

	public UUID getId() {
		return id;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public short getAge() {
		return age;
	}

	public String getCohort() {
		return cohort;
	}

	public String getRegion() {
		return region;
	}

	public long getMonthlyIncome() {
		return monthlyIncome;
	}

	public String getIncomeBand() {
		return incomeBand;
	}

	public LocalDate getDataFrom() {
		return dataFrom;
	}

	public LocalDate getDataTo() {
		return dataTo;
	}

	void describe(short age, String cohort, String job, String archetype, String region,
		String householdType, short householdSize) {
		this.age = age;
		this.cohort = cohort;
		this.job = job;
		this.archetype = archetype;
		this.region = region;
		this.householdType = householdType;
		this.householdSize = householdSize;
	}

	void setFinance(long monthlyIncome, String incomeBand, String incomeRegularity, long targetMonthlySpend,
		BigDecimal targetSavingRate, BigDecimal targetInvestmentRate, boolean investParticipation,
		short riskScore, String riskAttitude) {
		this.monthlyIncome = monthlyIncome;
		this.incomeBand = incomeBand;
		this.incomeRegularity = incomeRegularity;
		this.targetMonthlySpend = targetMonthlySpend;
		this.targetSavingRate = targetSavingRate;
		this.targetInvestmentRate = targetInvestmentRate;
		this.investParticipation = investParticipation;
		this.riskScore = riskScore;
		this.riskAttitude = riskAttitude;
	}

	void setDataRange(LocalDate from, LocalDate to) {
		this.dataFrom = from;
		this.dataTo = to;
	}
}
