package com.gagastudio.finmate.mate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RuntimeMateCandidateRepository {
	private static final String QUERY = """
		WITH current_persona AS (
			SELECT DISTINCT ON (p.source_persona_id)
				p.source_persona_id, p.release_version, p.age_band, p.occupation_group,
				p.income_band, p.spending_tendency, p.saving_rate_band, p.investment_tendency,
				p.household_type, p.lifestyle_tags, p.last_synced_at
			FROM finmate_synthetic_runtime_persona p
			WHERE p.peer_discovery_opt_in = TRUE
				AND p.data_state = 'FRESH'
				AND NOT EXISTS (
					SELECT 1
					FROM finmate_user_synthetic_persona_binding owner_binding
					JOIN finmate_user owner_user ON owner_user.id = owner_binding.user_id
					WHERE owner_binding.source_persona_id = p.source_persona_id
						AND owner_binding.release_version = p.release_version
						AND owner_user.anonymous_card_opt_in = FALSE
				)
				%s
			ORDER BY p.source_persona_id, p.last_synced_at DESC, p.release_version DESC
		)
		SELECT p.source_persona_id AS adventurer_id, p.release_version, p.age_band, p.occupation_group,
			p.income_band, p.spending_tendency, p.saving_rate_band, p.investment_tendency,
			p.household_type, p.lifestyle_tags, p.last_synced_at, f.feature_month AS data_as_of,
			f.lifestyle_cluster_id AS source_group_id,
			r.source_routine AS routine_id, r.domain AS routine_domain,
			r.frequency AS routine_frequency, r.maintained_months
		FROM current_persona p
		JOIN finmate_synthetic_runtime_feature_profile f
			ON f.source_persona_id = p.source_persona_id
			AND f.release_version = p.release_version
		JOIN LATERAL (
			SELECT routine.source_routine, routine.domain, routine.frequency, routine.maintained_months
			FROM finmate_synthetic_runtime_routine routine
			WHERE routine.source_persona_id = p.source_persona_id
				AND routine.release_version = p.release_version
				AND routine.maintained_months >= 1
				AND (
					(routine.domain = 'SAVING' AND
						lower(replace(btrim(routine.source_routine), ' ', '_')) IN ('자동저축', 'automatic_saving'))
					OR
					(routine.domain = 'SPENDING' AND
						lower(replace(btrim(routine.source_routine), ' ', '_')) IN ('카페방문', '카페_방문', 'cafe_visit'))
				)
			ORDER BY routine.maintained_months DESC, routine.source_routine ASC
			LIMIT 1
		) r ON TRUE
		ORDER BY p.source_persona_id ASC
		""";
	private static final String SEARCH_PREDICATE = """
		AND p.income_band = :incomeBand
		AND p.saving_rate_band = :savingRateBand
		AND NOT EXISTS (
			SELECT 1
			FROM finmate_user_synthetic_persona_binding binding
			WHERE binding.user_id = :userId
				AND binding.source_persona_id = p.source_persona_id
		)
		""";
	private static final String DETAIL_PREDICATE = """
		AND NOT EXISTS (
			SELECT 1
			FROM finmate_user_synthetic_persona_binding binding
			WHERE binding.user_id = :userId
				AND binding.source_persona_id = p.source_persona_id
		)
		""";

	private final NamedParameterJdbcTemplate jdbc;

	RuntimeMateCandidateRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<RuntimeMateCandidate> findEligible(UUID userId, String incomeBand, String savingRateBand) {
		return query(SEARCH_PREDICATE, Map.of(
			"userId", userId,
			"incomeBand", incomeBand,
			"savingRateBand", savingRateBand));
	}

	Optional<RuntimeMateCandidate> findDiscoverableById(UUID userId, String adventurerId) {
		return query(DETAIL_PREDICATE, Map.of("userId", userId)).stream()
			.filter(candidate -> candidate.adventurerId().equals(adventurerId))
			.findFirst();
	}

	private List<RuntimeMateCandidate> query(String predicate, Map<String, ?> parameters) {
		return jdbc.query(QUERY.formatted(predicate), parameters, this::mapCandidate);
	}

	private RuntimeMateCandidate mapCandidate(ResultSet result, int rowNumber) throws SQLException {
		String sourcePersonaId = result.getString("adventurer_id");
		String releaseVersion = result.getString("release_version");
		return new RuntimeMateCandidate(
			sourcePersonaId,
			OpaqueAdventurerId.from(releaseVersion, sourcePersonaId),
			result.getString("age_band"),
			result.getString("occupation_group"),
			result.getString("income_band"),
			result.getString("spending_tendency"),
			result.getString("saving_rate_band"),
			result.getString("investment_tendency"),
			result.getString("household_type"),
			result.getString("lifestyle_tags"),
			result.getTimestamp("last_synced_at").toInstant(),
			result.getDate("data_as_of").toLocalDate(),
			"synthetic-runtime",
			result.getString("routine_id"),
			result.getString("routine_domain"),
			result.getString("routine_frequency"),
			result.getInt("maintained_months"));
	}

}
