package com.gagastudio.finmate.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gagastudio.finmate.diary.DiaryController;
import com.gagastudio.finmate.diary.DiaryFacade;
import com.gagastudio.finmate.metrics.FeedService;
import com.gagastudio.finmate.metrics.OverviewService;
import com.gagastudio.finmate.metrics.ProjectionService;
import com.gagastudio.finmate.mission.MissionService;
import com.gagastudio.finmate.metrics.PeerCompareService;
import com.gagastudio.finmate.metrics.PeriodType;

/**
 * 로그인한 사람의 화면.
 *
 * persona를 경로로 받던 것을 토큰에서 꺼내도록 바꿨다. 경로로 받으면 남의 id를 넣어
 * 남의 원장을 볼 수 있다 — 금융 데이터에서는 그것만으로 끝이다.
 *
 * 앱이 부르는 자리는 전부 여기다. persona 경로는 남겨 두되 인증을 요구한다(개발용).
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final CurrentPersona current;
	private final OverviewService overviews;
	private final PeerCompareService peers;
	private final DiaryFacade diaries;
	private final FeedService feed;
	private final MissionService missions;
	private final ProjectionService projections;

	public MeController(CurrentPersona current, OverviewService overviews, PeerCompareService peers,
		DiaryFacade diaries, FeedService feed, MissionService missions, ProjectionService projections) {
		this.current = current;
		this.overviews = overviews;
		this.peers = peers;
		this.diaries = diaries;
		this.feed = feed;
		this.missions = missions;
		this.projections = projections;
	}

	/** 그 사람의 기준일. 화면의 "오늘"이 벽시계가 아니라 데이터에서 나온다. */
	private LocalDate today() {
		return overviews.of(current.personaId(), PeriodType.MONTHLY).referenceDate();
	}

	/** 마이 탭 한 화면 */
	@GetMapping("/overview")
	public OverviewService.Overview overview(@RequestParam(defaultValue = "daily") String period) {
		return overviews.of(current.personaId(), PeriodType.from(period));
	}

	/** 피드 상단 "그룹 보기" + 내 위치 */
	@GetMapping("/peers")
	public PeerCompareService.Comparison peers(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
		return peers.forPersona(current.personaId(),
			month != null ? month : overviews.of(current.personaId(), PeriodType.MONTHLY).referenceDate());
	}

	// ── 피드 ──

	@GetMapping("/feed/groups")
	public java.util.List<FeedService.Group> groups() {
		return feed.groupsFor(current.personaId(), today());
	}

	@GetMapping("/feed/mates")
	public java.util.List<FeedService.Mate> mates(@RequestParam(defaultValue = "12") int limit) {
		return feed.matesInBand(current.personaId(), today(), Math.min(limit, 50));
	}

	// ── 미션 ──

	public record MissionScreen(
		long points,
		java.util.List<MissionService.Progress> inProgress,
		java.util.List<MissionService.Progress> recommended,
		java.util.List<MissionService.DayMark> keepStreak) {
	}

	@GetMapping("/missions")
	public MissionScreen missions() {
		UUID persona = current.personaId();
		LocalDate today = today();
		return new MissionScreen(
			missions.points(persona),
			missions.inProgress(persona, today),
			missions.recommended(persona, today),
			missions.keepStreak(persona, today));
	}

	@PostMapping("/missions/{missionId}")
	public ResponseEntity<Void> accept(@PathVariable String missionId) {
		boolean added = missions.accept(current.personaId(), missionId, today());
		return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).build();
	}

	/** 달성한 미션의 보상을 정산한다. 같은 날 여러 번 불러도 두 번 주지 않는다. */
	@PostMapping("/missions/settle")
	public java.util.Map<String, Object> settle() {
		UUID persona = current.personaId();
		int granted = missions.settle(persona, today());
		return java.util.Map.of("granted", granted, "points", missions.points(persona));
	}

	// ── 인사이트 ──

	@GetMapping("/projection")
	public ProjectionService.Projection projection() {
		return projections.of(current.personaId(), today());
	}

	@GetMapping("/projection/delay")
	public java.util.Map<String, Object> delay(@RequestParam long price) {
		int days = projections.delayDays(current.personaId(), today(), price);
		return days < 0
			? java.util.Map.of("possible", false, "reason", "매달 남는 돈이 없어 계산할 수 없습니다")
			: java.util.Map.of("possible", true, "delayDays", days);
	}

	// ── 그림일기 ──

	@PostMapping("/diary/{date}")
	public ResponseEntity<DiaryController.StatusResponse> requestDiary(
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		var result = diaries.request(current.personaId(), date);
		return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
			.body(result.status());
	}

	@GetMapping("/diary/{date}")
	public DiaryController.StatusResponse diary(
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return diaries.status(current.personaId(), date);
	}

	@GetMapping("/diary/{date}/image")
	public ResponseEntity<byte[]> diaryImage(
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.body(diaries.image(current.personaId(), date));
	}
}
