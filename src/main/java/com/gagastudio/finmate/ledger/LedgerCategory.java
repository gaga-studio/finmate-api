package com.gagastudio.finmate.ledger;

import java.util.Map;

/**
 * 데이터셋의 분류를 앱의 카테고리로 옮긴다.
 *
 * 데이터셋은 `타입 / 대분류 / 소분류` 3단이고 조합이 33종이다. 앱은 처음에 9종만 갖고 있었다
 * (식비·카페·교통·쇼핑·구독·여가·저축·투자·수입). 모자란 쪽을 접어 넣지 않고 **앱을 넓혔다** —
 * 월세를 '쇼핑'에, 학원비를 '여가'에 넣으면 화면이 거짓말을 하고, 그 화면을 근거로 만드는
 * 미션과 또래 비교까지 틀어진다.
 *
 * 매핑 단위는 대분류다. 딱 한 곳만 소분류를 본다 — '생활'이 편의점·마트·통신과
 * 서비스구독을 함께 담고 있는데, 구독은 앱이 이미 따로 보여주던 축이라 갈랐다.
 */
public enum LedgerCategory {
	FOOD("food"),
	CAFE("cafe"),
	TRANSPORT("transport"),
	SHOPPING("shopping"),
	SUBSCRIPTION("subscription"),
	ENTERTAINMENT("entertainment"),
	LIVING("living"),
	HOUSING("housing"),
	EDUCATION("education"),
	BEAUTY("beauty"),
	HEALTH("health"),
	INSURANCE("insurance"),
	TRAVEL("travel"),
	SAVING("saving"),
	INVEST("invest"),
	INCOME("income");

	private final String wireName;

	LedgerCategory(String wireName) {
		this.wireName = wireName;
	}

	/** 앱 타입(`Category`)이 쓰는 이름. DB와 JSON에 이 값이 나간다. */
	public String wireName() {
		return wireName;
	}

	private static final Map<String, LedgerCategory> BY_MAJOR = Map.ofEntries(
		Map.entry("식비", FOOD),
		Map.entry("카페/간식", CAFE),
		Map.entry("교통", TRANSPORT),
		Map.entry("패션/쇼핑", SHOPPING),
		Map.entry("온라인쇼핑", SHOPPING),
		Map.entry("문화/여가", ENTERTAINMENT),
		Map.entry("주거", HOUSING),
		Map.entry("교육/학습", EDUCATION),
		Map.entry("뷰티/미용", BEAUTY),
		Map.entry("의료/건강", HEALTH),
		Map.entry("금융", INSURANCE),
		Map.entry("여행/숙박", TRAVEL));

	/**
	 * 흐름이 먼저다. 저축·투자·소득은 대분류를 보지 않아도 정해지고,
	 * 실제로 데이터셋의 '이체/저축'과 '이체/투자'가 그렇게 들어온다.
	 */
	public static LedgerCategory of(String flow, String major, String minor) {
		switch (flow) {
			case "소득" -> {
				return INCOME;
			}
			case "저축" -> {
				return SAVING;
			}
			case "투자" -> {
				return INVEST;
			}
			default -> {
			}
		}
		if ("생활".equals(major)) {
			return "서비스구독".equals(minor) ? SUBSCRIPTION : LIVING;
		}
		LedgerCategory mapped = BY_MAJOR.get(major);
		if (mapped == null) {
			// 조용히 '기타'로 접으면 새 분류가 생긴 걸 아무도 모르게 된다. 적재를 멈춘다.
			throw new IllegalArgumentException(
				"매핑되지 않은 분류입니다: flow=%s major=%s minor=%s".formatted(flow, major, minor));
		}
		return mapped;
	}
}
