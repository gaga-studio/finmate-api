package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoutineCandidateGenerator {
	private static final List<String> DIFFICULTIES = List.of("LIGHT", "STANDARD", "CHALLENGE");
	private static final Set<String> DOMAINS = Set.of("SPENDING", "SAVING", "INVESTMENT_JUDGMENT");

	public List<RoutineCandidate> generate(String domain) {
		return generate(domain, 500_000L);
	}

	public List<RoutineCandidate> generate(String domain, long standardMonthlyAmountKrw) {
		if (!DOMAINS.contains(domain)) throw new InvalidAdaptationDomainException();
		return DIFFICULTIES.stream().map(difficulty -> candidate(domain, difficulty, standardMonthlyAmountKrw)).toList();
	}

	public Set<String> availableDomains() {
		return DOMAINS;
	}

	private RoutineCandidate candidate(String domain, String difficulty, long standardMonthlyAmountKrw) {
		String suffix = difficulty.toLowerCase();
		if ("INVESTMENT_JUDGMENT".equals(domain)) {
			return new RoutineCandidate("candidate-" + suffix, difficulty, domain, difficultyTitle(difficulty, "review"), "BEHAVIOR",
				null, null, "Review one decision using the routine checklist", List.of("Record the reason before acting"));
		}
		if ("SPENDING".equals(domain)) {
			int days = switch (difficulty) {
				case "LIGHT" -> 1;
				case "STANDARD" -> 3;
				default -> 7;
			};
			return new RoutineCandidate("candidate-" + suffix, difficulty, domain,
				"예산을 %d일 먼저 확인".formatted(days), "BEHAVIOR", null, null,
				"%d일 동안 하루 예산을 확인".formatted(days), List.of("오늘 예산 확인", "하루 지출 기록"));
		}
		long amount = roundToTenThousand(switch (difficulty) {
			case "LIGHT" -> standardMonthlyAmountKrw * 0.6;
			case "STANDARD" -> standardMonthlyAmountKrw;
			default -> standardMonthlyAmountKrw * 1.4;
		});
		return new RoutineCandidate("candidate-" + suffix, difficulty, domain,
			"월급날 %d만원 먼저 저축".formatted(amount / 10_000),
			"AMOUNT_KRW", amount, null, null, List.of("월급 입금일 확인", "입금 당일 자동저축 확인"));
	}

	private long roundToTenThousand(double amount) {
		return Math.max(10_000L, Math.round(amount / 10_000.0) * 10_000L);
	}

	private String difficultyTitle(String difficulty, String routine) {
		return difficulty.substring(0, 1) + difficulty.substring(1).toLowerCase() + " " + routine;
	}
}
