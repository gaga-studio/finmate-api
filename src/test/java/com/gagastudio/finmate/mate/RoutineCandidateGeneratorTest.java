package com.gagastudio.finmate.mate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutineCandidateGeneratorTest {
	private final RoutineCandidateGenerator generator = new RoutineCandidateGenerator();

	@Test
	void generatesExactlyOneCandidateForEachDifficulty() {
		var candidates = generator.generate("SAVING");

		assertThat(candidates).hasSize(3);
		assertThat(candidates).extracting(RoutineCandidate::difficulty)
			.containsExactlyInAnyOrder("LIGHT", "STANDARD", "CHALLENGE");
		assertThat(candidates).extracting(RoutineCandidate::domain).containsOnly("SAVING");
	}

	@Test
	void investmentJudgmentCandidatesAreBehaviorOnly() {
		var candidates = generator.generate("INVESTMENT_JUDGMENT");

		assertThat(candidates).allSatisfy(candidate -> {
			assertThat(candidate.targetKind()).isEqualTo("BEHAVIOR");
			assertThat(candidate.behaviorTarget()).isNotBlank();
			assertThat(candidate.targetAmountKrw()).isNull();
			assertThat(candidate.targetRatioBps()).isNull();
		});
	}

	@Test
	void financialKnowledgeIsNotAnAdaptationDomain() {
		assertThat(generator.availableDomains()).containsExactlyInAnyOrder("SPENDING", "SAVING", "INVESTMENT_JUDGMENT");
		assertThat(generator.availableDomains()).doesNotContain("FINANCIAL_KNOWLEDGE");
	}
}
