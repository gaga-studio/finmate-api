package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoutineCandidateGenerator {
	private static final List<String> DIFFICULTIES = List.of("LIGHT", "STANDARD", "CHALLENGE");
	private static final Set<String> DOMAINS = Set.of("SPENDING", "SAVING", "INVESTMENT_JUDGMENT");

	public List<RoutineCandidate> generate(String domain) {
		if (!DOMAINS.contains(domain)) throw new InvalidAdaptationDomainException();
		return DIFFICULTIES.stream().map(difficulty -> candidate(domain, difficulty)).toList();
	}

	public Set<String> availableDomains() {
		return DOMAINS;
	}

	private RoutineCandidate candidate(String domain, String difficulty) {
		String suffix = difficulty.toLowerCase();
		if ("INVESTMENT_JUDGMENT".equals(domain)) {
			return new RoutineCandidate("candidate-" + suffix, difficulty, domain, difficultyTitle(difficulty, "review"), "BEHAVIOR",
				null, null, "Review one decision using the routine checklist", List.of("Record the reason before acting"));
		}
		long amount = switch (difficulty) {
			case "LIGHT" -> 50_000L;
			case "STANDARD" -> 80_000L;
			default -> 120_000L;
		};
		return new RoutineCandidate("candidate-" + suffix, difficulty, domain, difficultyTitle(difficulty, "weekly routine"), "AMOUNT_KRW",
			amount, null, null, List.of("Complete the weekly routine"));
	}

	private String difficultyTitle(String difficulty, String routine) {
		return difficulty.substring(0, 1) + difficulty.substring(1).toLowerCase() + " " + routine;
	}
}
