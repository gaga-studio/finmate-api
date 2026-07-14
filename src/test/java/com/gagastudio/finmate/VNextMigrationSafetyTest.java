package com.gagastudio.finmate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VNextMigrationSafetyTest {
	@Test
	void resetsDemoOnlyStateBeforeExpandingTheTimelineFrameContract() throws IOException {
		String resource = "/db/migration/V13__vnext_runtime_contract.sql";
		try (InputStream input = getClass().getResourceAsStream(resource)) {
			assertThat(input).as(resource).isNotNull();
			String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			String deleteCommands = "DELETE FROM finmate_demo_timeline_command;\nDELETE FROM finmate_demo_fixture_state;";
			assertThat(sql).contains(deleteCommands);
			assertThat(sql.indexOf(deleteCommands)).isLessThan(sql.indexOf("ALTER TABLE finmate_demo_fixture_state"));
		}
	}
}
