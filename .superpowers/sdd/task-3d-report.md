# Task 3D Report: Quest, record, and demo timeline backend

## Scope delivered

- Added authenticated quest list/detail/completion operations with six representative quests: daily spending, weekly saving, public transit, risk-profile diagnosis, ETF O/X knowledge, and a seven-day knowledge streak.
- Quest completions return XP and internal reward codes only. `financialStatsChanged` is always `false`; no quest operation changes the goal, raid, or financial statistics.
- Synthetic-MyData quests transition to `DATA_PENDING`, retain the original idempotency key, and become completed only after a demo timeline snapshot provides evidence.
- Added user-scoped daily record range/day/reflection operations. Ranges are inclusive and limited to 31 days; reflection writes do not call any financial calculation service.
- Added profile-gated `POST /api/v1/demo/timeline/advance`. It is registered only in the `demo` profile, checks `EUROPE_TRAVEL_JANUARY` plus the expected stage, persists command results for replay, and rejects stale stages with `409 DATA_STALE`.
- Demo stage updates reuse the public `SyntheticSnapshotIngestionService`, therefore applying the established goal/raid high-water recalculation and audit behavior. Stage three reaches KRW 5,000,000, 10,000 BPS, and zero boss HP.
- Added V4 Flyway persistence for quests, reward codes, completions, record events/reflections, demo fixture state, and timeline commands.

## Owned files

- `src/main/java/com/gagastudio/finmate/quests/**`
- `src/main/java/com/gagastudio/finmate/records/**`
- `src/main/java/com/gagastudio/finmate/goals/Demo*.java`
- `src/main/java/com/gagastudio/finmate/goals/InvalidDemoTimelineException.java`
- `src/main/resources/db/migration/V4__quests_records_and_demo_timeline.sql`
- `src/test/java/com/gagastudio/finmate/QuestRecordIntegrationTests.java`
- `src/test/java/com/gagastudio/finmate/DemoTimelineIntegrationTests.java`
- `.superpowers/sdd/task-3d-report.md`

## TDD evidence

1. RED: `QuestRecordIntegrationTests.listsSixRepresentativeQuestsWithoutChangingFinancialStats` failed with the quest route absent. GREEN: the seeded list projection passed.
2. RED: completion, pending evidence, record range, reflection invariance, and user-isolation tests failed against absent routes/state transitions. GREEN: the same focused class passed after the minimal persistence and API implementation.
3. RED: the demo-profile timeline test failed because no demo route existed. GREEN: the profile-gated controller and shared snapshot-ingestion implementation passed all three stages, stale-stage, replay, record, and synthetic-evidence assertions.
4. RED: a pending quest's original idempotency key failed after evidence arrived. GREEN: persisting the pending completion and awarding it at verification made the original key replay the completed XP result.
5. RED: a stage-one timeline replay after stage three returned later raid metadata. GREEN: the immutable timeline command projection now preserves the original copy key and prior stage response.

## Verification

- `FINMATE_APP_ORIGIN=http://localhost:3000 ./gradlew test --tests com.gagastudio.finmate.QuestRecordIntegrationTests --tests com.gagastudio.finmate.DemoTimelineIntegrationTests`: `BUILD SUCCESSFUL in 13s`.
- The normal shared-output full run encountered concurrent Gradle report-file replacement from other work in the shared checkout; no test assertion failure was reported.
- Isolated full verification: `FINMATE_APP_ORIGIN=http://localhost:3000 ./gradlew --no-daemon -I /tmp/finmate-task3d-isolated-build.gradle test`: `BUILD SUCCESSFUL in 23s`.

## Review remediation

- Added V6, without modifying Mate-owned V5. It records the original nullable request stage and a complete immutable JSON response for every new demo command; existing V4 command rows are backfilled from their stored command, goal, raid, and snapshot projections.
- Quest commands now lock the authenticated user and target quest before resolving idempotency. Same-key concurrent requests replay one stored completion; a different key or a same key for a different quest returns `409 IDEMPOTENCY_KEY_REUSED` rather than leaking a uniqueness failure.
- Pending synthetic-MyData completions retain `202 DATA_PENDING` on replay until the evidence batch completes the quest, then replay the completed XP response.
- Demo commands lock the user before fixture lookup/creation, eliminating first-state and command-insert races. Replays require the same expected stage; changed requests return `409 IDEMPOTENCY_KEY_REUSED`.
- Demo replays now return the persisted JSON projection, so later direct snapshot ingestion, including XP changes, cannot alter the original response.
- `expectedStage` is now `Integer` with `@NotNull`, preventing omitted JSON fields from being treated as stage zero.

## Review tests

1. RED: pending quest replay incorrectly returned `200`; GREEN: it remains `202` until evidence completion.
2. RED: concurrent same-key quest completion raised a PostgreSQL uniqueness violation and different keys both completed; GREEN: same-key returns two `200` replays and different keys return `200` plus `409`.
3. RED: omitted demo `expectedStage` advanced stage zero, same key/different stage replayed, concurrent demo advances leaked a uniqueness violation, and later shared ingestion changed a replay. GREEN: validation, conflicts, first/subsequent concurrency replays, and immutable JSON replay all pass.
4. FINAL: `FINMATE_APP_ORIGIN=http://localhost:3000 ./gradlew --no-daemon -I /tmp/finmate-task3d-isolated-build.gradle test`: `BUILD SUCCESSFUL in 24s`.
