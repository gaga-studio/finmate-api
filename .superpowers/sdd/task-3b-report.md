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
