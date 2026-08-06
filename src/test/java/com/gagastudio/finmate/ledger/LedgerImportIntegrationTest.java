package com.gagastudio.finmate.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 적재가 옳은지, 그리고 두 번 돌려도 같은지 확인한다.
 *
 * 데이터셋은 저장소에 없다(전체 884MB). 파이프라인으로 재생성해야 하므로,
 * 없으면 테스트를 건너뛴다 — CI에서 빨간불이 뜨는 게 아니라 "이 검증은 데이터가 있을 때만
 * 의미가 있다"는 사실을 그대로 드러내는 쪽을 골랐다.
 *
 * <pre>
 *   cd finmate-data
 *   python3 pipeline/05_generate.py
 * </pre>
 */
@SpringBootTest
class LedgerImportIntegrationTest extends PostgresIntegrationTest {

	/** 통합 테스트가 89만 행을 다 넣을 이유는 없다. 규칙이 맞는지는 소수로도 드러난다. */
	private static final int SAMPLE = 30;

	@Autowired
	private LedgerImporter importer;

	@Autowired
	private JdbcTemplate jdbc;

	/**
	 * 컨테이너를 클래스마다 새로 띄우지 않으므로 앞 테스트가 넣은 행이 남는다.
	 * "몇 명 들어왔는가"를 세는 테스트들이라 시작점이 0이어야 의미가 있다.
	 */
	@BeforeEach
	void 원장을_비운다() {
		jdbc.execute("TRUNCATE ledger_entry, persona CASCADE");
	}

	private static Path bundlesDir() {
		return Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
	}

	@Test
	void 번들을_적재하면_인구와_원장이_함께_들어온다() {
		assumeThat(Files.isDirectory(bundlesDir()))
			.as("finmate-data 번들이 필요합니다. pipeline/05_generate.py를 먼저 실행하세요")
			.isTrue();

		LedgerImporter.Result result = importer.importFrom(bundlesDir(), SAMPLE);

		assertThat(result.personas()).isEqualTo(SAMPLE);
		assertThat(result.entries()).isGreaterThan(SAMPLE * 100);

		Long personas = jdbc.queryForObject("SELECT count(*) FROM persona", Long.class);
		Long entries = jdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class);
		assertThat(personas).isEqualTo(SAMPLE);
		assertThat(entries).isEqualTo(result.entries());

		// 거래가 한 건도 없는 사람이 있으면 화면이 빈 채로 뜬다
		Long empty = jdbc.queryForObject("""
			SELECT count(*) FROM persona p
			WHERE NOT EXISTS (SELECT 1 FROM ledger_entry e WHERE e.persona_id = p.id)
			""", Long.class);
		assertThat(empty).isZero();
	}

	@Test
	void 두_번_적재해도_행이_늘지_않는다() {
		assumeThat(Files.isDirectory(bundlesDir())).isTrue();

		importer.importFrom(bundlesDir(), SAMPLE);
		Long afterFirst = jdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class);

		LedgerImporter.Result second = importer.importFrom(bundlesDir(), SAMPLE);
		Long afterSecond = jdbc.queryForObject("SELECT count(*) FROM ledger_entry", Long.class);

		assertThat(second.entries()).isZero();
		assertThat(afterSecond).isEqualTo(afterFirst);
	}

	@Test
	void 흐름과_카테고리가_약속된_값으로만_들어온다() {
		assumeThat(Files.isDirectory(bundlesDir())).isTrue();
		importer.importFrom(bundlesDir(), SAMPLE);

		List<String> flows = jdbc.queryForList("SELECT DISTINCT flow FROM ledger_entry", String.class);
		assertThat(flows).containsOnly("소비", "저축", "소득", "투자");

		List<String> categories = jdbc.queryForList("SELECT DISTINCT category FROM ledger_entry", String.class);
		List<String> known = java.util.Arrays.stream(LedgerCategory.values()).map(LedgerCategory::wireName).toList();
		assertThat(categories).isSubsetOf(known);

		// 흐름과 카테고리가 어긋나면 그림일기의 "그날의 주인공" 판정이 통째로 틀어진다
		Long mismatched = jdbc.queryForObject("""
			SELECT count(*) FROM ledger_entry
			WHERE (flow = '소득' AND category <> 'income')
			   OR (flow = '저축' AND category <> 'saving')
			   OR (flow = '투자' AND category <> 'invest')
			""", Long.class);
		assertThat(mismatched).isZero();
	}

	@Test
	void 수입은_양수_지출은_음수로_들어온다() {
		assumeThat(Files.isDirectory(bundlesDir())).isTrue();
		importer.importFrom(bundlesDir(), SAMPLE);

		Map<String, Object> wrongSign = jdbc.queryForMap("""
			SELECT
			  count(*) FILTER (WHERE flow = '소득' AND amount <= 0) AS income_not_positive,
			  count(*) FILTER (WHERE flow = '소비' AND amount >= 0) AS spend_not_negative
			FROM ledger_entry
			""");
		assertThat(wrongSign.get("income_not_positive")).isEqualTo(0L);
		assertThat(wrongSign.get("spend_not_negative")).isEqualTo(0L);
	}

	@Test
	void 원장_일자가_프로필이_밝힌_기간_안에_있다() {
		assumeThat(Files.isDirectory(bundlesDir())).isTrue();
		importer.importFrom(bundlesDir(), SAMPLE);

		// 화면이 "이번 달"을 어디로 잡을지가 persona.data_from~data_to에서 나온다.
		// 원장이 그 밖으로 새면 월 전환 화면이 빈다.
		Long outside = jdbc.queryForObject("""
			SELECT count(*) FROM ledger_entry e
			JOIN persona p ON p.id = e.persona_id
			WHERE e.occurred_on < p.data_from OR e.occurred_on > p.data_to
			""", Long.class);
		assertThat(outside).isZero();
	}
}
