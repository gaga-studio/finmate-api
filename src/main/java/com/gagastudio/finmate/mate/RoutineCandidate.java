package com.gagastudio.finmate.mate;

import java.util.List;

public record RoutineCandidate(String candidateId, String difficulty, String domain, String title, String targetKind,
	Long targetAmountKrw, Integer targetRatioBps, String behaviorTarget, List<String> steps) {
}
