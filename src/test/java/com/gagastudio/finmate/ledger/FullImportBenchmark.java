package com.gagastudio.finmate.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.support.PostgresIntegrationTest;

/**
 * 2,000명 전체를 실제로 적재한다. 평소 테스트에는 넣지 않는다 — 분 단위로 걸리고,
 * 확인하려는 것이 "규칙이 맞는가"가 아니라 "이 규모가 감당되는가"라서 성격이 다르다.
 *
 * <pre>
 *   FINMATE_FULL_IMPORT=1 ./gradlew test --tests '*FullImportBenchmark'
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FINMATE_FULL_IMPORT", matches = "1")
class FullImportBenchmark extends PostgresIntegrationTest {

	@Autowired
	private LedgerImporter importer;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 전체_적재() {
		Path bundles = Path.of(System.getProperty("user.home"),
			"Projects", "finmate-data", "outputs", "finmate_v3", "bundles");
		assumeThat(Files.isDirectory(bundles)).isTrue();

		jdbc.execute("TRUNCATE ledger_entry, persona CASCADE");
		LedgerImporter.Result result = importer.importFrom(bundles, 0);

		System.out.printf("%n  persona %d명 · 거래 %,d행 · %,dms (%.0f행/초)%n",
			result.personas(), result.entries(), result.millis(),
			result.entries() / (result.millis() / 1000.0));

		assertThat(result.personas()).isEqualTo(2000);
		assertThat(result.entries()).isGreaterThan(800_000);
	}
}
