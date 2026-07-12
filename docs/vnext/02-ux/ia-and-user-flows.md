# FinMate vNext information architecture and flows

## Navigation

Authenticated navigation has exactly four tabs:

1. `홈`: confirmed `UserGoal`, `RaidView`, sync state, and next quest.
2. `메이트`: group list, anonymous adventurer cards, routine detail, adaptation, and active build.
3. `퀘스트`: available/active/pending/completed quests and XP/internal rewards.
4. `기록`: daily records, reflection, and monthly report.

Authentication and onboarding precede the tab shell. Settings are reached from the home header and are not a tab.

## Flow A: first run

`sign up/login -> synthetic baseline -> enter Europe travel goal -> review 2,000,000 / 5,000,000 KRW and January 2027 -> explicit confirm -> 홈`

Back navigation before confirmation preserves the draft. Onboarding completion creates exactly one main goal.

## Flow B: mate routine import

`메이트 -> group -> anonymous adventurer -> routine -> choose one domain -> compare LIGHT/STANDARD/CHALLENGE -> import -> ActiveRoutineBuild`

The user cannot enter routine detail without group and adventurer context. Import never changes the main goal. When a build already exists, the confirmation screen names the current build and the candidate build; cancel preserves the current build, while explicit confirmation archives it and activates the new one.

## Flow C: quest and financial evidence

`퀘스트 -> start -> complete behavior -> receive XP/internal reward -> 홈 remains financially unchanged -> synthetic MyData recalculation -> updated RaidView`

For a financial-evidence quest, completion enters `DATA_PENDING` until recalculation. Stale or insufficient data shows the last sync time and a single recovery action.

## Flow D: record

`기록 -> date -> DailyRecord -> add/edit reflection`

The record distinguishes quest events, routine-build activity, and MyData recalculation. Reflection does not modify calculated values.

## Demo-only flow

The presenter advances fixture stages through the demo-profile API. No production UI calls or exposes the demo operation.
