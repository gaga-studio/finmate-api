package com.gagastudio.finmate.goals;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SyntheticRuntimePersonaRepository extends JpaRepository<SyntheticRuntimePersona, SyntheticRuntimePersonaId> {
	@Query("""
		select persona from SyntheticRuntimePersona persona
		where persona.id.releaseVersion = :releaseVersion
		  and persona.peerDiscoveryOptIn = true
		  and persona.incomeRegularity = :incomeRegularity
		  and persona.householdType = :householdType
		  and not exists (
			  select binding from SyntheticPersonaBinding binding
			  where binding.sourcePersonaId = persona.id.sourcePersonaId
			    and binding.releaseVersion = persona.id.releaseVersion
		  )
		""")
	List<SyntheticRuntimePersona> findBindingCandidates(@Param("releaseVersion") String releaseVersion,
		@Param("incomeRegularity") String incomeRegularity, @Param("householdType") String householdType);

	@Query("""
		select persona from SyntheticRuntimePersona persona
		where persona.id.releaseVersion = :releaseVersion
		  and persona.peerDiscoveryOptIn = true
		  and persona.id.sourcePersonaId <> :excludedSourcePersonaId
		""")
	List<SyntheticRuntimePersona> findDiscoverableExcludingSourcePersonaId(@Param("releaseVersion") String releaseVersion,
		@Param("excludedSourcePersonaId") String excludedSourcePersonaId);
}
