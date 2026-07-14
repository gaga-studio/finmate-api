package com.gagastudio.finmate.goals;

record DemoStageSnapshot(int frameIndex, String month, long savingEventKrw, long amountKrw,
	int spendingBps, int savingBps, int investmentJudgmentBps) {
	static DemoStageSnapshot forFrame(int frameIndex) {
		return switch (frameIndex) {
			case 0 -> new DemoStageSnapshot(0, "2026-08", 500_000, 2_500_000, 5_300, 2_400, 4_400);
			case 1 -> new DemoStageSnapshot(1, "2026-09", 500_000, 3_000_000, 5_400, 2_800, 4_600);
			case 2 -> new DemoStageSnapshot(2, "2026-10", 500_000, 3_500_000, 5_500, 3_200, 4_800);
			case 3 -> new DemoStageSnapshot(3, "2026-11", 500_000, 4_000_000, 5_700, 3_800, 5_200);
			case 4 -> new DemoStageSnapshot(4, "2026-12", 500_000, 4_500_000, 5_900, 4_400, 5_600);
			case 5 -> new DemoStageSnapshot(5, "2027-01", 500_000, 5_000_000, 6_200, 5_000, 6_000);
			default -> throw new IllegalArgumentException("Unsupported demo frame");
		};
	}
}
