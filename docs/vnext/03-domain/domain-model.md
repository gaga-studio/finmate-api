# FinMate vNext domain contract

## 1. Shared value types

- `KrwAmount`: signed 64-bit JSON integer representing whole KRW; decimals and floating point are forbidden.
- `BasisPoints`: integer `0..10000`; `10000` means 100%.
- `Timestamp`: ISO 8601 date-time with an offset or `Z`.
- `DataState`: `FRESH | PENDING | STALE | INSUFFICIENT`.
- Every calculated read includes `calculationVersion`, `dataState`, and nullable `lastSyncedAt` (`null` only for insufficient/no-sync states).

## 2. UserGoal

`UserGoal` is the main outcome confirmed during onboarding. It contains identity, title, domain, current and target values, target month, confirmation time, state, and calculated metadata.

Invariants:

- **UG-1:** A user has exactly one main goal after onboarding and at most one active main goal at all times.
- **UG-2:** Onboarding completion is the explicit confirmation boundary.
- **UG-3:** Routine discovery, adaptation, import, replacement, or archival never creates, mutates, replaces, pauses, or completes `UserGoal`.
- **UG-4:** The representative fixture is Europe travel, `2000000` current KRW, `5000000` target KRW, target month `2027-01`.

## 3. RecommendedAdventurerCard

Discovery is `MateGroup -> RecommendedAdventurerCard -> Routine`. Cards are anonymous and expose no source user identifier, exact account balance, transaction, employer, location detail, or investment holding.

- **RA-1:** An adventurer request is scoped by `groupId`; a routine request is scoped by both `adventurerId` and `routineId`.
- **RA-2:** An operational group has `memberCount >= 30`. `memberCount = 10` is allowed only with `syntheticDemo = true`, and demo groups are excluded from production aggregation.
- **RA-3:** Adaptation has exactly one selected domain and candidates `LIGHT`, `STANDARD`, `CHALLENGE`.
- **RA-4:** `SPENDING` and `SAVING` may use `AMOUNT_KRW`, `RATIO_BPS`, or `BEHAVIOR`. `INVESTMENT_JUDGMENT` and financial-knowledge routines use `BEHAVIOR` only.

## 4. RoutineAdaptationCandidate and ActiveRoutineBuild

An adaptation set belongs to one source routine. Selecting a domain generates three immutable candidates. Importing one creates an `ActiveRoutineBuild` with steps, status, source references, and calculated metadata.

- **ARB-1:** At most one build has `status = ACTIVE` globally per user, independent of goal.
- **ARB-2:** Import does not overwrite. If a build is active, import returns `ACTIVE_ROUTINE_BUILD_EXISTS` unless `confirmReplacement = true` is explicitly supplied to the replacement command.
- **ARB-3:** Confirmed replacement atomically sets the old build to `ARCHIVED`, records `archivedAt` and `replacedByBuildId`, and creates the new active build with `replacesBuildId`.
- **ARB-4:** Build completion may affect routine adherence and quests, never the main goal value directly.

## 5. RaidView

`RaidView` is a read model of the confirmed main goal and recalculated financial evidence. It has stage, boss HP in basis points, financial stats, copy key, and calculated metadata.

- Raid financial stats are projections from synthetic MyData recalculation.
- XP and internal rewards are displayed separately from financial stats.
- Lower or stale financial evidence cannot be disguised as quest-driven progress.

## 6. Quest

`Quest` has lifecycle `AVAILABLE -> ACTIVE -> DATA_PENDING | COMPLETED | EXPIRED | CANCELLED`. A behavior-only quest may verify immediately. A financial-evidence quest remains pending until a synthetic sync verifies it.

- **Q-1:** Completion grants `xpAwarded >= 0` and zero or more approved internal reward codes. Cash or cash-equivalent rewards are forbidden.
- **Q-2:** Completion does not change spending, saving, or investment-judgment stats. Those change only through MyData recalculation.

## 7. DailyRecord

`DailyRecord` is a dated projection of quest events, build activity, financial recalculation, XP, and reflection. It preserves the evidence source and calculated metadata. Reflections do not rewrite financial calculations.

## 8. First-release adapters

- Auth mechanism: `EMAIL_PASSWORD`.
- MyData provider: `SYNTHETIC`.
- Coach copy provider: `DETERMINISTIC_APPROVED_COPY`.
- Runtime generation, real brokerage/investment execution, cash rewards, and public ranking have no domain aggregate or event.
