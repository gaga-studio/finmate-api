package com.gagastudio.finmate.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 실제 Postgres에 Flyway를 태우고 도는 통합 테스트의 공통 바탕.
 *
 * H2로 대신하지 않는다. 이 백엔드가 풀 문제는 원장 집계와 인덱스라
 * 방언이 다른 DB에서 통과하는 테스트는 근거가 되지 못한다.
 *
 * 컨테이너는 static이라 클래스마다 새로 뜨지 않는다. JVM이 끝나면 Ryuk이 정리한다.
 */
public abstract class PostgresIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		// 인증 테스트가 서명 키 없이 컨텍스트를 못 띄우면 안 된다
		registry.add("finmate.jwt-secret", () -> "test-secret-that-is-long-enough-for-hs256-signing");
	}
}
