# Task 3B Report: Main goal, synthetic baseline, raid and report backend

## Scope delivered

- Added authenticated `GET /api/v1/onboarding`, `PUT /api/v1/onboarding`, `GET /api/v1/goals/active`, `GET /api/v1/home`, `GET /api/v1/raids/current`, and `GET /api/v1/reports/monthly?month=YYYY-MM`.
- Added a V2 Flyway migration for onboarding state, the one active main goal, synthetic financial snapshots, raid projections, and raid-projection audit records.
- Completing onboarding validates an explicitly confirmed goal, rejects invalid amounts or past target months, creates the Europe-travel-shaped synthetic baseline (5200 spending BPS, 1800 saving BPS, 4000 investment-judgment BPS, XP 0), and initializes raid progress at zero against the confirmed current amount.
- The onboarding command requires a 16-128 character `Idempotency-Key`; a same-key retry returns the original completed onboarding view and a different key after goal creation returns `ACTIVE_MAIN_GOAL_EXISTS`.
- Financial progress is calculated solely from financial evidence. XP remains available for activity/reporting but cannot affect goal progress. Raid storage keeps current and highest progress separately, with 33/66/100 stage thresholds and approved copy keys only.
- Monthly reports read the stored synthetic snapshot and return zero activity values until quest activity exists. Months without a snapshot return an `INSUFFICIENT` calculated view.

## Owned files

- `src/main/java/com/gagastudio/finmate/goals/**`
- `src/main/resources/db/migration/V2__goals_synthetic_baseline_and_raids.sql`
- `src/test/java/com/gagastudio/finmate/goals/GoalRulesTest.java`
- `src/test/java/com/gagastudio/finmate/goals/FinancialGoalProgressTest.java`
- `src/test/java/com/gagastudio/finmate/GoalHomeRaidReportIntegrationTests.java`
- `.superpowers/sdd/task-3b-report.md`

## TDD evidence

1. RED: `./gradlew test --tests com.gagastudio.finmate.goals.GoalRulesTest` failed at test compilation because `GoalValidator`, `GoalDraft`, `InvalidMainGoalException`, and `GoalProgress` did not exist.
2. GREEN: after the minimal validation and financial-progress implementation, the same command completed `BUILD SUCCESSFUL in 1s`.
3. RED: `FINMATE_APP_ORIGIN=http://localhost:3000 ./gradlew test --tests com.gagastudio.finmate.GoalHomeRaidReportIntegrationTests.requiresAnIdempotencyKeyForOnboardingCompletion` failed because onboarding accepted a missing idempotency key.
4. GREEN: after persisting and checking the onboarding idempotency key, the same command completed `BUILD SUCCESSFUL in 6s`.

## Verification

- `FINMATE_APP_ORIGIN=http://localhost:3000 ./gradlew test --tests com.gagastudio.finmate.goals.GoalRulesTest --tests com.gagastudio.finmate.goals.FinancialGoalProgressTest --tests com.gagastudio.finmate.GoalHomeRaidReportIntegrationTests`: `BUILD SUCCESSFUL in 6s`.
- `./gradlew test`: `BUILD SUCCESSFUL in 10s`.
- `git diff --check` for owned source, migration, and test paths: no output.

## Review remediation (2026-07-13)

- Added public `SyntheticSnapshotIngestionService`, `SyntheticSnapshotInput`, and `SyntheticSnapshotResult` types as the reusable normal/demo ingestion boundary for Task 3D.
- Each ingestion transaction locks the active goal, stores goal-owned synthetic evidence, updates the goal current amount, recalculates current and highest progress, preserves the highest unlocked stage, calculates stage-local boss HP, updates the raid projection, and appends an ownership-scoped audit row.
- Raid UI progress, unlocked stage, and boss HP use the high-water mark. A lower later snapshot records lower current progress without relocking or healing the raid; 10000 BPS remains stage 3 with zero boss HP.
- Onboarding locks the authenticated `finmate_user` row before claiming the command. Concurrent same-key calls both return the one completed result; concurrent different-key calls return one success and one RFC 7807 `ACTIVE_MAIN_GOAL_EXISTS` conflict.
- Goal exceptions now use the shared `ApiProblems` factory, including `instance`, `code`, and `traceId`.
- Required request amounts are nullable boxed values with `@NotNull`; goal titles are capped at 255 characters.
- V2 now uses composite ownership foreign keys for snapshots, raid projections, and raid audits. Snapshot indexes and report queries include the active goal ID, preventing historical or cross-user evidence from leaking into current reports.
- Added the requested boundary, stage-local HP, regression, XP invariance, two-user isolation, concurrent key, missing amount, missing snapshot, historical goal, ownership constraint, and shared-problem tests.

## Review TDD and verification evidence

1. RED: `./gradlew test --tests com.gagastudio.finmate.goals.GoalRulesTest` failed at `compileTestJava` with the expected missing `bossHpBpsForHighestProgress` method. The same compiler pass also reported concurrent Task 3C symbols that were still being written and were not modified by Task 3B.
2. GREEN: `./gradlew test --tests com.gagastudio.finmate.goals.GoalRulesTest --tests com.gagastudio.finmate.goals.FinancialGoalProgressTest --tests com.gagastudio.finmate.GoalHomeRaidReportIntegrationTests` completed `BUILD SUCCESSFUL in 9s`; 25 tests ran with 0 failures, 0 errors, and 0 skipped.
3. FINAL: `./gradlew test` completed `BUILD SUCCESSFUL in 13s`; 59 tests ran with 0 failures, 0 errors, and 0 skipped.
4. `git diff --check` for owned source, migration, tests, and this report produced no output.
