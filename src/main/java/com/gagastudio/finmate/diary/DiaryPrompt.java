package com.gagastudio.finmate.diary;

import java.util.Map;

/**
 * 그날의 거래를 그림 프롬프트로 옮긴다.
 *
 * 그림이 원장에서 나와야 한다. 아무 예쁜 그림이나 붙이면 "내 하루의 기록"이 아니라 배경화면이다.
 * 그래서 장면은 그날 가장 크게 쓴 거래의 카테고리에서 고른다.
 *
 * 가맹점 이름을 프롬프트에 넣지 않는다. 실명 상표가 그림에 글자로 새겨지기도 하고,
 * 합성 데이터라도 특정 브랜드를 그림으로 만들 이유가 없다. 카테고리까지만 쓴다.
 */
final class DiaryPrompt {

	private DiaryPrompt() {
	}

	/** 카테고리 → 그림으로 그릴 장면. 사람이 그 행동을 하고 있는 순간이어야 한다. */
	private static final Map<String, String> SCENE = Map.ofEntries(
		Map.entry("food", "eating a warm home-style meal at a small restaurant table"),
		Map.entry("cafe", "holding an iced coffee at a bright cafe counter"),
		Map.entry("transport", "riding a subway train, holding the handle strap"),
		Map.entry("shopping", "carrying shopping bags out of a small store"),
		Map.entry("subscription", "watching something on a phone, relaxed on a sofa"),
		Map.entry("entertainment", "reading a book beside a window"),
		Map.entry("living", "picking up snacks at a convenience store"),
		Map.entry("housing", "standing at the doorway of a small apartment"),
		Map.entry("education", "taking notes at a desk with a laptop open"),
		Map.entry("beauty", "choosing skincare bottles on a shelf"),
		Map.entry("health", "receiving a small paper bag at a pharmacy counter"),
		Map.entry("insurance", "reviewing a document at a desk, calm"),
		Map.entry("travel", "walking with a small suitcase at a station"),
		Map.entry("saving", "dropping coins into a savings jar, satisfied"),
		Map.entry("invest", "watching a rising chart on a phone screen, hopeful"),
		Map.entry("income", "receiving an envelope, smiling"));

	private static final String SUBJECT = "a young Korean woman in her twenties";

	/**
	 * 세 조각을 붙인다 — 그림체 · 장면 · 형식 제약.
	 *
	 * 글자를 빼는 지시를 굳이 세 번 겹쳐 쓴다. 이미지 모델은 간판·상표를 자주 그려 넣고,
	 * 그렇게 들어간 글자는 대개 뜻 없는 문자열이라 화면에서 바로 티가 난다.
	 */
	static String build(ArtStyle style, String category) {
		String scene = SCENE.getOrDefault(category, "going about an ordinary day in the city");
		return "%s. %s %s. square composition, single centered subject, warm and gentle mood. "
			.formatted(style.promptFragment(), SUBJECT, scene)
			+ "no text, no letters, no numbers, no logos, no watermark, no signage";
	}
}
