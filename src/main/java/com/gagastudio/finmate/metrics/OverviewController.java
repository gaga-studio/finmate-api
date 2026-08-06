package com.gagastudio.finmate.metrics;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이 탭 한 화면을 한 번에 돌려준다.
 *
 * 예산·저축·투자·소득·소비 탑5를 따로 부르면 화면 하나에 왕복이 다섯 번이고,
 * 그때마다 기준일과 기간 경계를 다시 계산해 서로 어긋날 여지가 생긴다.
 *
 * 지금은 persona를 경로로 받는다. 로그인한 사람과 합성 인구를 잇는 일은 아직 안 했고,
 * 그 전에 원장 위에서 화면이 도는지부터 확인하려는 것이다.
 */
@RestController
@RequestMapping("/api/v1/personas/{personaId}")
public class OverviewController {

	private final OverviewService overviews;

	public OverviewController(OverviewService overviews) {
		this.overviews = overviews;
	}

	@GetMapping("/overview")
	public OverviewService.Overview overview(
		@PathVariable UUID personaId,
		@RequestParam(defaultValue = "daily") String period) {
		return overviews.of(personaId, PeriodType.from(period));
	}
}
