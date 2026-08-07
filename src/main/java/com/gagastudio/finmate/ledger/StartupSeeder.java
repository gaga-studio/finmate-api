package com.gagastudio.finmate.ledger;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gagastudio.finmate.metrics.MonthlyRollup;

/**
 * 서버를 띄울 때 데이터를 채운다. 부하 테스트와 로컬 시연에서 쓴다.
 *
 * 기본으로 꺼 둔다. 켜져 있으면 서버가 뜰 때마다 89만 행을 훑게 되고,
 * 무엇보다 운영에서 이런 게 돌면 안 된다.
 *
 * 이미 들어 있으면 아무것도 하지 않는다 — 적재가 멱등이라 다시 돌려도 결과는 같지만,
 * 매번 26초를 기다릴 이유가 없다.
 */
@Component
@ConditionalOnProperty(name = "finmate.seed-on-start", havingValue = "true")
public class StartupSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StartupSeeder.class);

	private final LedgerImporter importer;
	private final MonthlyRollup rollup;
	private final JdbcTemplate jdbc;
	private final Path bundlesDir;
	private final int limit;

	public StartupSeeder(LedgerImporter importer, MonthlyRollup rollup, JdbcTemplate jdbc,
		@Value("${finmate.seed-bundles:}") String bundlesDir,
		@Value("${finmate.seed-limit:0}") int limit) {
		this.importer = importer;
		this.rollup = rollup;
		this.jdbc = jdbc;
		this.bundlesDir = bundlesDir.isBlank()
			? Path.of(System.getProperty("user.home"), "Projects", "finmate-data",
				"outputs", "finmate_v3", "bundles")
			: Path.of(bundlesDir);
		this.limit = limit;
	}

	@Override
	public void run(org.springframework.boot.ApplicationArguments args) {
		Long existing = jdbc.queryForObject("SELECT count(*) FROM persona", Long.class);
		if (existing != null && existing > 0) {
			log.info("이미 {}명이 들어 있어 적재를 건너뜁니다", existing);
			return;
		}
		if (!Files.isDirectory(bundlesDir)) {
			log.warn("번들이 없어 적재를 건너뜁니다: {} (finmate-data에서 pipeline/05_generate.py 실행)", bundlesDir);
			return;
		}
		LedgerImporter.Result result = importer.importFrom(bundlesDir, limit);
		rollup.rebuildAll();
		log.info("시드 완료 — {}명 · {}행", result.personas(), result.entries());
	}
}
