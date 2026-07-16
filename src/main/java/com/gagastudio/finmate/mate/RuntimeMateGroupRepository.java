package com.gagastudio.finmate.mate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeMateGroupRepository {
	private static final String SELECT_GROUPS = """
		SELECT source_group_id, description, member_count, average_age,
			average_spending_defense_bps, average_saving_hp_bps,
			average_investment_judgment_bps, average_consumption_rate_bps,
			average_saving_rate_bps, data_as_of
		FROM finmate_synthetic_runtime_group_profile
		ORDER BY source_group_id
		""";
	private final NamedParameterJdbcTemplate jdbc;

	RuntimeMateGroupRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<RuntimeMateGroupProfile> findAll() {
		return jdbc.query(SELECT_GROUPS, Map.of(), this::mapGroup);
	}

	Optional<RuntimeMateGroupProfile> findById(String groupId) {
		return jdbc.query(SELECT_GROUPS.replace("ORDER BY source_group_id", "WHERE source_group_id = :groupId"),
			Map.of("groupId", groupId), this::mapGroup).stream().findFirst();
	}

	RuntimeMateGroupRange ranges(String groupId) {
		return jdbc.queryForObject("""
			SELECT
				COALESCE(percentile_cont(0.25) WITHIN GROUP (ORDER BY consumption_rate_bps), 0)::integer AS spending_p25,
				COALESCE(percentile_cont(0.50) WITHIN GROUP (ORDER BY consumption_rate_bps), 0)::integer AS spending_median,
				COALESCE(percentile_cont(0.75) WITHIN GROUP (ORDER BY consumption_rate_bps), 0)::integer AS spending_p75,
				COALESCE(percentile_cont(0.25) WITHIN GROUP (ORDER BY saving_rate_bps), 0)::integer AS saving_p25,
				COALESCE(percentile_cont(0.50) WITHIN GROUP (ORDER BY saving_rate_bps), 0)::integer AS saving_median,
				COALESCE(percentile_cont(0.75) WITHIN GROUP (ORDER BY saving_rate_bps), 0)::integer AS saving_p75
			FROM finmate_synthetic_runtime_feature_profile
			WHERE 'cluster-' || lifestyle_cluster_id = :groupId
			""", Map.of("groupId", groupId), (result, rowNumber) -> new RuntimeMateGroupRange(
			result.getInt("spending_p25"), result.getInt("spending_median"), result.getInt("spending_p75"),
			result.getInt("saving_p25"), result.getInt("saving_median"), result.getInt("saving_p75")));
	}

	private RuntimeMateGroupProfile mapGroup(ResultSet result, int rowNumber) throws SQLException {
		Number averageAge = (Number) result.getObject("average_age");
		return new RuntimeMateGroupProfile(result.getString("source_group_id"), result.getString("description"),
			result.getInt("member_count"), averageAge == null ? null : averageAge.doubleValue(),
			result.getInt("average_spending_defense_bps"), result.getInt("average_saving_hp_bps"),
			result.getInt("average_investment_judgment_bps"), result.getInt("average_consumption_rate_bps"),
			result.getInt("average_saving_rate_bps"), result.getDate("data_as_of").toLocalDate());
	}
}
