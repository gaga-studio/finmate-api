# Task 3C Report: Mate discovery and routine-build backend

## Scope delivered

- Added authenticated mate-group discovery, scoped anonymous adventurer recommendations, and scoped routine detail.
- Added Flyway V3 tables and deterministic fixtures for one 34-member operational group and one explicitly synthetic 10-member demo group. The database constraint prevents any 10-member production-eligible group.
- Added per-user routine adaptations that begin in `AWAITING_DOMAIN`, allow one source-routine domain choice, and produce exactly `LIGHT`, `STANDARD`, and `CHALLENGE` candidates.
- Added behavior-only investment-judgment candidates. Financial knowledge is excluded from adaptation domains; quantitative candidates are limited to spending and saving.
- Added one-global-active-build persistence, idempotent import, conflict handling, and transactional confirmed replacement with archived/replacement linkage.
- Routine commands only use mate/routine tables. They do not inject or mutate `UserGoal`; integration coverage compares the complete active-goal response before and after import/replacement.

## Owned files

- `src/main/java/com/gagastudio/finmate/mate/**`
- `src/main/resources/db/migration/V3__mate_discovery_and_routine_builds.sql`
- `src/test/java/com/gagastudio/finmate/mate/RoutineCandidateGeneratorTest.java`
- `src/test/java/com/gagastudio/finmate/MateRoutineBuildIntegrationTests.java`
- `.superpowers/sdd/task-3c-report.md`

## TDD evidence

1. RED: `./gradlew test --tests 'com.gagastudio.finmate.mate.RoutineCandidateGeneratorTest'` failed because `RoutineCandidateGenerator` and `RoutineCandidate` did not exist. The shared worktree also contained unrelated in-progress goal test errors at that moment.
2. GREEN: in a disposable checkout at baseline `37c9759` with only Task 3C files overlaid, `./gradlew test --tests 'com.gagastudio.finmate.mate.RoutineCandidateGeneratorTest' --tests 'com.gagastudio.finmate.MateRoutineBuildIntegrationTests'` initially exposed a replacement foreign-key ordering failure.
3. GREEN: after archiving and flushing the old build before inserting and linking the replacement, the same targeted command completed successfully.

## Verification

- Baseline checkout with Task 3C files: `./gradlew test`: `BUILD SUCCESSFUL in 10s`.
- Shared branch: `./gradlew test`: `BUILD SUCCESSFUL in 12s`.
- `git diff --check`: no output.
