package com.gagastudio.finmate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.metrics.FeedService;
import com.gagastudio.finmate.metrics.MonthlyRollup;
import com.gagastudio.finmate.metrics.ProjectionService;
import com.gagastudio.finmate.mission.MissionService;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 피드·미션·인사이트가 원장과 맞는지, 그리고 남의 정보를 흘리지 않는지 본다.
 *
 * 이 셋은 전부 "남의 돈 이야기"나 "보상"을 다룬다. 계산이 틀리면 숫자가 조금 이상한 게 아니라
 * 개인 정보가 새거나 포인트가 무한히 불어난다.
 */
@SpringBootTest(properties = "finmate.art.provider=stub")
class ScreensIntegrationTest extends PostgresIntegrationTest {

	private static final int SAMPLE = 120;

	@Autowired private LedgerImporter importer;
	@Autowired private MonthlyRollup rollup;
	@Autowired private FeedService feed;
	@Autowired private MissionService missions;
	@Autowired private ProjectionService projections;
	@Autowired private JdbcTemplate jdbc;

	private UUID persona;
	private LocalDate month;

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@BeforeEach
	void 적재한다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다").isTrue();
		jdbc.execute("TRUNCATE ledger_entry, persona, persona_month, diary_entry, "
			+ "persona_mission, point_ledger CASCADE");
		importer.importFrom(bundlesDir(), SAMPLE);
		rollup.rebuildAll();
		persona = jdbc.queryForObject("SELECT id FROM persona ORDER BY external_id LIMIT 1", UUID.class);
		month = jdbc.queryForObject("SELECT max(month) FROM persona_month", LocalDate.class);
	}

	// ── 피드 ──

	@Test
	void 그룹은_사람이_충분할_때만_만들어진다() {
		List<FeedService.Group> groups = feed.groupsFor(persona, month);
		// 세 명짜리 그룹의 평균은 사실상 개인 정보다
		assertThat(groups).allSatisfy(g -> assertThat(g.members()).isGreaterThanOrEqualTo(20));
	}

	@Test
	void 그룹_인원이_실제_인원과_같다() {
		List<FeedService.Group> groups = feed.groupsFor(persona, month);
		var income = groups.stream().filter(g -> g.id().equals("g-income")).findFirst();
		assumeThat(income).isPresent();

		Integer actual = jdbc.queryForObject("""
			SELECT count(*) FROM persona
			WHERE income_band = (SELECT income_band FROM persona WHERE id = ?)
			""", Integer.class, persona);
		// 화면에 1,570명이라 적혀 있는데 실제로 안 그러면 그 숫자가 거짓말이 된다
		assertThat(income.get().members()).isEqualTo(actual);
	}

	@Test
	void 메이트_목록에_남의_금액이_그대로_나가지_않는다() {
		List<FeedService.Mate> mates = feed.matesInBand(persona, month, 10);
		assertThat(mates).isNotEmpty();

		for (FeedService.Mate m : mates) {
			// 구간 표기까지만. "월 70만원대"는 되고 "713,400원"은 안 된다.
			assertThat(m.spendBand()).matches("월 \\d+만원대|기록 없음");
			assertThat(m.budgetLeftPct()).isBetween(0, 100);
			// 카테고리는 이름만, 금액 없이
			assertThat(m.topCategories()).allSatisfy(c -> assertThat(c).doesNotMatch(".*\\d.*"));
		}
	}

	@Test
	void 메이트_목록에_내가_들어가지_않는다() {
		String myName = jdbc.queryForObject(
			"SELECT display_name FROM persona WHERE id = ?", String.class, persona);
		List<FeedService.Mate> mates = feed.matesInBand(persona, month, 50);
		assertThat(mates).noneSatisfy(m -> assertThat(m.nickname()).isEqualTo(myName));
	}

	// ── 미션 ──

	@Test
	void 예산_도장판이_원장에서_판정된다() {
		List<MissionService.DayMark> marks = missions.keepStreak(persona, month.plusDays(20));
		assertThat(marks).isNotEmpty();

		for (MissionService.DayMark d : marks) {
			Long spentAgain = jdbc.queryForObject("""
				SELECT COALESCE(-sum(amount), 0) FROM ledger_entry
				WHERE persona_id = ? AND occurred_on = ? AND flow = '소비'
				""", Long.class, persona, d.date());
			assertThat(d.spent()).isEqualTo(spentAgain);
			// 사용자가 누르는 게 아니라 그날 쓴 금액이 성패를 정한다
			assertThat(d.kept()).isEqualTo(d.spent() <= d.limit());
		}
	}

	@Test
	void 같은_날_여러_번_정산해도_포인트가_불어나지_않는다() {
		LocalDate today = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?", LocalDate.class, persona);
		missions.accept(persona, "quiz-emergency", today);

		missions.settle(persona, today);
		long after = missions.points(persona);
		assertThat(after).isPositive();

		// 화면이 새로고침될 때마다 포인트가 불어나면 안 된다
		for (int i = 0; i < 5; i++) {
			assertThat(missions.settle(persona, today)).isZero();
		}
		assertThat(missions.points(persona)).isEqualTo(after);
	}

	@Test
	void 담기_전의_지출로_보상을_주지_않는다() {
		LocalDate today = jdbc.queryForObject(
			"SELECT max(occurred_on) FROM ledger_entry WHERE persona_id = ?", LocalDate.class, persona);
		missions.accept(persona, "keep-daily-budget", today);

		LocalDate accepted = jdbc.queryForObject(
			"SELECT accepted_on FROM persona_mission WHERE persona_id = ? AND mission_id = ?",
			LocalDate.class, persona, "keep-daily-budget");
		assertThat(accepted).isEqualTo(today);
	}

	@Test
	void 담은_미션은_추천에서_사라진다() {
		LocalDate today = month;
		int before = missions.recommended(persona, today).size();
		missions.accept(persona, "quiz-emergency", today);
		assertThat(missions.recommended(persona, today)).hasSize(before - 1);
	}

	// ── 인사이트 ──

	@Test
	void 투영이_실적_뒤에_붙고_경계가_표시된다() {
		ProjectionService.Projection p = projections.of(persona, month);

		assertThat(p.points()).isNotEmpty();
		long actual = p.points().stream().filter(ProjectionService.Point::actual).count();
		long projected = p.points().stream().filter(pt -> !pt.actual()).count();
		assertThat(actual).isPositive();
		assertThat(projected).isEqualTo(6);

		// 실적이 먼저, 투영이 뒤. 섞이면 화면이 경계를 그릴 수 없다.
		var list = p.points();
		int firstProjected = 0;
		while (firstProjected < list.size() && list.get(firstProjected).actual()) {
			firstProjected++;
		}
		assertThat(list.subList(firstProjected, list.size()))
			.allSatisfy(pt -> assertThat(pt.actual()).isFalse());
	}

	@Test
	void 투영의_근거를_함께_내려준다() {
		ProjectionService.Projection p = projections.of(persona, month);
		// 예측이 아니라 연장이라는 것을 화면이 말할 수 있어야 한다
		assertThat(p.basis()).contains("예측이 아닙니다");
		assertThat(p.basisMonths()).isEqualTo(3);
	}

	@Test
	void 저축이_많을수록_자산_곡선이_내려가지_않는다() {
		// 저축·투자는 나가는 돈이지만 사라지는 게 아니다.
		// 이걸 헷갈리면 열심히 저축할수록 자산이 줄어드는 그래프가 나온다.
		for (UUID p : jdbc.queryForList("SELECT id FROM persona LIMIT 30", UUID.class)) {
			Long saved = jdbc.queryForObject(
				"SELECT COALESCE(sum(saved + invested), 0) FROM persona_month WHERE persona_id = ?",
				Long.class, p);
			if (saved == null || saved == 0) {
				continue;
			}
			var proj = projections.of(p, month);
			var actualPoints = proj.points().stream().filter(ProjectionService.Point::actual).toList();
			assumeThat(actualPoints).isNotEmpty();
			// 순증 = 소득 - 소비. 저축액이 여기서 빠지면 안 된다.
			Long check = jdbc.queryForObject(
				"SELECT COALESCE(sum(earned - spend), 0) FROM persona_month WHERE persona_id = ? AND month <= ?",
				Long.class, p, month);
			assertThat(actualPoints.get(actualPoints.size() - 1).netWorth()).isEqualTo(check);
		}
	}

	@Test
	void 매달_남는_돈이_없으면_지연일수를_계산하지_않는다() {
		// -1을 그냥 내려주면 화면이 "-1일 늦어집니다"라고 쓴다
		for (UUID p : jdbc.queryForList("SELECT id FROM persona LIMIT 40", UUID.class)) {
			int days = projections.delayDays(p, month, 2_000_000);
			assertThat(days).satisfiesAnyOf(
				d -> assertThat(d).isEqualTo(-1),
				d -> assertThat(d).isPositive());
		}
	}
}
