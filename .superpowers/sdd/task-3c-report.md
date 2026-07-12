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

## CHANGES_REQUIRED remediation

- Added additive `V5__routine_idempotency_commands.sql`; V3 remains unchanged. The new table uses `(user_id, operation, idempotency_key)` as its primary key and stores a SHA-256 request fingerprint, original HTTP status/body, and import or replacement result identifiers.
- Added a database trigger that rejects updates and deletes from routine idempotency command rows.
- Import and replacement now lock the authenticated `finmate_user` row before command lookup or active-build mutation, serializing both operations per user.
- Same-key retries deserialize the immutable original response snapshot. Fingerprint mismatches return HTTP 409 with `IDEMPOTENCY_KEY_REUSED` before any build mutation.
- Build unique-key races are translated to `ACTIVE_ROUTINE_BUILD_EXISTS`; idempotency inserts use `ON CONFLICT` and replay lookup instead of leaking a database exception.
- `confirmReplacement=false` is rejected by request validation before the transactional service is invoked.

## Review TDD evidence

1. RED: delayed import replay failed because the original build row had become `ARCHIVED` after replacement.
2. GREEN: import replay passed after responses moved to the immutable command snapshot.
3. RED: delayed replacement replay failed because later replacement mutated the first replacement's active-build history.
4. GREEN: replacement replay passed after storing its original archived/active response and identifiers in V5.
5. The expanded PostgreSQL integration matrix covers invalid group thresholds, cross-user isolation, import and replacement fingerprint mismatch, false confirmation with zero writes, immutable command rows, concurrent imports, concurrent replacement retries, mixed import/replacement races, history links, and unchanged main goal.

## Review verification

- `./gradlew test --tests 'com.gagastudio.finmate.MateRoutineBuildIntegrationTests' --tests 'com.gagastudio.finmate.mate.*'`: `BUILD SUCCESSFUL in 7s`.
- `./gradlew test`: `BUILD SUCCESSFUL in 17s` (80 tests, zero failures).
- `./gradlew test --rerun-tasks`: `BUILD SUCCESSFUL in 19s` (80 tests, zero failures; all tasks executed).
- `git diff --check` for Task 3C source, test, report, and V5 paths: no output.
