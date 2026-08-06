package com.gagastudio.finmate.ledger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 거래 한 건. 화면의 모든 수치가 여기서 파생된다.
 *
 * 앱은 지표를 저장하지 않는다 — 예산도, 저축 진행률도, 순자산도 전부 이 원장을 다시 세어 만든다.
 * 그래서 이 테이블의 크기와 인덱스가 곧 응답 시간이다 (2,000명 × 평균 444건 = 89만 행).
 *
 * `persona`를 연관관계(@ManyToOne)가 아니라 UUID로 들고 있다. 원장은 89만 행을 배치로 넣고
 * 집계로만 읽는 테이블이라, 매 행마다 persona 프록시를 붙이면 적재도 조회도 느려지기만 한다.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "persona_id", nullable = false)
	private UUID personaId;

	@Column(name = "external_id", nullable = false, length = 32)
	private String externalId;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	/** DB가 occurred_at에서 만들어 주는 생성 컬럼. 애플리케이션은 읽기만 한다. */
	@Column(name = "occurred_on", insertable = false, updatable = false)
	private LocalDate occurredOn;

	@Column(nullable = false, length = 120)
	private String merchant;

	/** 원 단위. 지출 음수, 수입 양수. */
	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 16)
	private String category;

	/** 소비 · 저축 · 소득 · 투자. 그림일기의 "그날의 주인공" 판정이 이걸 쓴다. */
	@Column(nullable = false, length = 8)
	private String flow;

	@Column(nullable = false, length = 16)
	private String major;

	@Column(nullable = false, length = 24)
	private String minor;

	@Column(name = "rule_id", length = 64)
	private String ruleId;

	@Column(name = "payment_method", length = 40)
	private String paymentMethod;

	@Column(length = 200)
	private String memo;

	protected LedgerEntry() {
	}

	public LedgerEntry(UUID personaId, String externalId, LocalDateTime occurredAt, String merchant,
		long amount, LedgerCategory category, String flow, String major, String minor,
		String ruleId, String paymentMethod, String memo) {
		this.personaId = personaId;
		this.externalId = externalId;
		this.occurredAt = occurredAt;
		this.merchant = merchant;
		this.amount = amount;
		this.category = category.wireName();
		this.flow = flow;
		this.major = major;
		this.minor = minor;
		this.ruleId = ruleId;
		this.paymentMethod = paymentMethod;
		this.memo = memo;
	}

	public Long getId() {
		return id;
	}

	public UUID getPersonaId() {
		return personaId;
	}

	public String getExternalId() {
		return externalId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public LocalDate getOccurredOn() {
		return occurredOn;
	}

	public String getMerchant() {
		return merchant;
	}

	public long getAmount() {
		return amount;
	}

	public String getCategory() {
		return category;
	}

	public String getFlow() {
		return flow;
	}
}
