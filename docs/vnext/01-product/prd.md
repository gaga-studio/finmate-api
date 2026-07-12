# FinMate vNext product requirements

## 1. Product promise

FinMate turns a confirmed financial goal into an RPG journey. The user learns from anonymous peers' routines, adapts one routine to their circumstances, completes quests, and sees financial progress only when synthetic MyData evidence changes.

## 2. Release persona and fixture

The representative user is saving for Europe travel. They have `2000000` KRW, want `5000000` KRW by January 2027, and confirm this as their one main `UserGoal` during onboarding. All product examples use integer KRW and avoid implying investment returns.

## 3. Navigation

| Tab | Purpose |
| --- | --- |
| `홈` | Main goal, current raid, latest data state, and next action. |
| `메이트` | Group discovery, anonymous adventurer selection, routine inspection, adaptation, and import. |
| `퀘스트` | Available and active quests, verification state, XP, and internal rewards. |
| `기록` | Daily records, reflections, and monthly report. |

No fifth tab or separate future-planning destination exists.

## 4. Required flows

### 4.1 Authentication and onboarding

1. The user signs up with email, a 12–72 character password, and display name, or logs in with email and password. JSON returns a 15-minute bearer access token and nested user; the rotating opaque refresh token exists only in the HttpOnly `finmate_refresh` cookie.
2. The app loads the synthetic MyData baseline.
3. The user enters one main goal, reviews current and target amounts and target month, and explicitly confirms it.
4. Confirmation creates one `UserGoal`; onboarding cannot complete with zero or multiple goals.
5. The home tab opens with the confirmed goal and `RaidView`.

### 4.2 Mate discovery and routine adaptation

1. The user chooses a `MateGroup`.
2. The user chooses a `RecommendedAdventurerCard` from that group.
3. The user opens one routine.
4. The user selects one adaptation domain: spending, saving, or investment judgment.
5. The service returns exactly one `light`, one `standard`, and one `challenge` `RoutineAdaptationCandidate`, each with the matching difficulty constant.
6. Importing a candidate creates an `ActiveRoutineBuild`; it never changes the main goal.
7. If another build is active, the user must explicitly confirm replacement. The old build is archived and the new one is activated in one transaction.

An operational group has at least 30 eligible anonymous members. A ten-member group is permitted only when it is explicitly marked synthetic demo data; it must never enter production recommendation aggregation.

### 4.3 Quests and recalculation

Quest completion may grant integer XP and non-cash internal rewards. It does not modify financial stats. Spending, saving, and investment-judgment stats change only after a synthetic MyData sync and deterministic recalculation. Pending, stale, or insufficient data never produces invented progress.

### 4.4 Demo

The demo timeline advances only through `POST /api/v1/demo/timeline/advance`. The API exists only under the backend `demo` profile. Production returns no demo route.

## 5. Content and safety

- Coach messages come from versioned, deterministic approved copy keys.
- Investment judgment and financial knowledge are behavior-only.
- No real investment execution, product recommendation, return projection, cash reward, or public ranking is present.
- No runtime-generated coach text is present.
- There is no email verification or password-recovery flow in this release.

## 6. Acceptance criteria

- A new user can authenticate, confirm the Europe travel goal, and see home and raid data.
- Mate navigation cannot skip the group or anonymous-adventurer context.
- Adaptation returns one candidate per difficulty and respects domain target rules.
- Investment-judgment candidates require a behavior target and cannot contain KRW or basis-point target fields; financial knowledge is behavior-only and is not an adaptation domain in this release.
- Import with an existing build requires explicit replacement confirmation and preserves the archived build identifier.
- Quest completion changes XP/internal rewards but does not change financial stats before recalculation.
- Every calculated response reports calculation version, data state, and last sync time.
- Stale and insufficient states are visible and command-blocking cases are RFC 7807 responses.
