package com.gagastudio.finmate.goals;

record DemoStageSnapshot(long amountKrw, int spendingBps, int savingBps, int investmentJudgmentBps) {
	static DemoStageSnapshot forStage(int stage) {
		return switch (stage) {
			case 1 -> new DemoStageSnapshot(2_500_000, 4_800, 2_400, 4_400);
			case 2 -> new DemoStageSnapshot(3_500_000, 4_400, 3_500, 5_200);
			case 3 -> new DemoStageSnapshot(5_000_000, 4_000, 5_000, 6_000);
			default -> throw new IllegalArgumentException("Unsupported demo stage");
		};
	}
}
