package com.gagastudio.finmate.diary;

import java.time.LocalDate;

/**
 * 그림일기의 그림체.
 *
 * 앱 기획이 "계속해서 그림체가 달라진다"이므로 날마다 바뀌어야 한다. 무작위로 고르면
 * 같은 날을 다시 만들 때 다른 그림체가 나와서, 그림을 다시 생성했을 뿐인데 과거가 바뀐다.
 * 그래서 **날짜에서 결정적으로** 고른다 — 같은 날은 언제 만들어도 같은 그림체다.
 *
 * 기존 22장이 쓰던 7종에서 <b>로우폴리 3D를 뺐다.</b> flux/dev로는 나오지 않는다 —
 * 재질을 앞세우고("every surface is a flat shaded triangle"), 매끄러움을 명시적으로 배제하고
 * ("no smooth curves anywhere"), 인물 대신 사물 장면으로도 바꿔 봤지만 세 번 다 그냥 매끄러운
 * 3D 렌더가 나왔다. 얼굴을 부드럽게 그리려는 성향과 각진 메시가 부딪히는 것으로 보인다.
 *
 * 안 되는 그림체를 목록에 두면 그 요일마다 조용히 다른 그림이 나간다. 그래서 뺐다.
 * 모델을 바꾸거나 그 그림체만 다른 제공자에 맡기는 방법이 있지만, 그림체 하나를 위해
 * 제공자를 둘로 나눌 만한 이득이 아니라고 봤다. 여섯 종이면 한 주가 채워진다.
 *
 * <b>재질을 먼저 말한다.</b> 처음엔 "hand embroidery on linen, visible stitches"처럼 형용사를
 * 나열했는데, 나온 그림은 그냥 깔끔한 디지털 일러스트였다. "무엇을 그린 그림인가"가 아니라
 * <b>"이 이미지가 무엇으로 만들어졌는가"</b>를 먼저 말하니 그제야 실이 도드라지고 리넨 올이 보였다
 * ("the entire image made of colored thread stitched onto coarse linen"). 여섯 종 모두 그 형태로 썼다.
 */
public enum ArtStyle {
	POP_ART("a printed 1960s comic book page, the whole image made of offset ink on newsprint. "
		+ "visible ben-day dot halftone, thick black ink outlines, flat primary red yellow and blue, "
		+ "slight ink bleed and paper yellowing"),

	MINT_POSTER("a silkscreen travel poster, the whole image made of flat layered ink on textured "
		+ "poster paper. mint green and warm cream, simple geometric shapes, no gradients, "
		+ "visible screen-print grain"),

	RISOGRAPH("a risograph print, the whole image made of two soy ink layers on rough recycled paper. "
		+ "coral and teal only, visible paper fibers, halftone dots, slight layer misregistration"),

	PAPER_COLLAGE("a cut paper collage, the whole image built from torn and cut matte construction "
		+ "paper layered on cardboard. visible paper edges and fibers, soft drop shadows between "
		+ "layers, muted earth tones"),

	EMBROIDERY("an embroidered textile artwork, the entire image made of colored thread stitched onto "
		+ "coarse natural linen. visible satin stitch and running stitch, raised thread texture, "
		+ "frayed fabric weave, hand-sewn folk craft"),

	CLAY_3D("a stop-motion clay diorama photographed in a studio, everything sculpted from matte "
		+ "plasticine. visible fingerprint texture in the clay, rounded chunky shapes, pastel palette, "
		+ "soft shadows, shallow depth of field");

	private final String promptFragment;

	ArtStyle(String promptFragment) {
		this.promptFragment = promptFragment;
	}

	public String promptFragment() {
		return promptFragment;
	}

	/**
	 * 그날의 그림체. 날짜만으로 정해지므로 몇 번을 다시 만들어도 같다.
	 *
	 * 여섯 종이라 주기가 6이다. 7이 아니어서 오히려 요일과 어긋나 돌고,
	 * 같은 요일에 같은 그림체가 반복되지 않는다.
	 */
	public static ArtStyle forDate(LocalDate date) {
		ArtStyle[] all = values();
		return all[(int) Math.floorMod(date.toEpochDay(), all.length)];
	}
}
