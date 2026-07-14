package com.gagastudio.finmate.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeFeatureProjectionRepository {
	private final JdbcTemplate jdbcTemplate;

	RuntimeFeatureProjectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<RuntimeFeatureProfile> find(RuntimePersonaBinding binding) {
		return jdbcTemplate.query("""
			SELECT feature_month, consumption_rate_bps, saving_rate_bps, invest_rate_bps,
				defense_score_bps, saving_score_bps, invest_score_bps
			FROM finmate_synthetic_runtime_feature_profile
			WHERE source_persona_id = ? AND release_version = ? AND projection_version = ?
			""", (resultSet, rowNumber) -> new RuntimeFeatureProfile(
			resultSet.getDate("feature_month").toLocalDate(),
			nullableInteger(resultSet, "consumption_rate_bps"),
			nullableInteger(resultSet, "saving_rate_bps"),
			nullableInteger(resultSet, "invest_rate_bps"),
			nullableInteger(resultSet, "defense_score_bps"),
			nullableInteger(resultSet, "saving_score_bps"),
			nullableInteger(resultSet, "invest_score_bps")),
			binding.sourcePersonaId(), binding.releaseVersion(), binding.projectionVersion()).stream().findFirst();
	}

	private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}
}
