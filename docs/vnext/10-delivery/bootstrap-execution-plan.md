# FinMate vNext two-repository bootstrap plan

## Scope

This is the current execution sequence for the RPG first release. It implements the canonical contract across `gaga-studio/finmate-api` and `gaga-studio/finmate-web`; archived plans under `docs/legacy/finmate-vnext-pre-rpg/` do not apply.

## Repository ownership

| Repository | Owns |
| --- | --- |
| `gaga-studio/finmate-api` | Java 21/Spring Boot/PostgreSQL runtime, Flyway migrations, synthetic MyData, deterministic approved copy, OpenAPI, fixtures, and contract verification. |
| `gaga-studio/finmate-web` | React/TypeScript/Vite PWA, four-tab shell, generated API client, offline read states, and end-to-end product flow. |

The API contract lands before dependent generated-client changes. Financial calculations and state transitions remain API-owned; the web app renders returned values and never recreates them.

## Delivery sequence

1. Bootstrap repository build, CI, local PostgreSQL, API generation, and cross-repository handoff rules.
2. Lock the canonical vNext documents, 25-operation OpenAPI contract, schema-valid examples, and negative contract checks.
3. Implement email/password auth with 15-minute access tokens and rotating `finmate_refresh` cookie sessions.
4. Implement onboarding confirmation of the one main Europe-travel `UserGoal` and synthetic baseline.
5. Implement home, raid, and monthly report projections with calculation metadata and explicit stale/insufficient states.
6. Implement group-to-anonymous-adventurer-to-routine discovery, structural adaptation candidates, and the one global active routine build with confirmed replacement.
7. Implement quest XP/internal rewards, synthetic recalculation separation, daily records, and reflection.
8. Register demo timeline advancement only under the Spring `demo` profile.
9. Build the web `홈`, `메이트`, `퀘스트`, `기록` flows first against examples, then switch to the generated client.
10. Run API contract/unit verification and the cross-repository representative flow before publishing bootstrap branches.

## Representative flow

`signup -> confirm Europe travel goal (2,000,000 / 5,000,000 KRW, 2027-01) -> 홈 raid -> 메이트 group -> anonymous adventurer -> routine -> choose adaptation domain -> LIGHT/STANDARD/CHALLENGE -> import active build -> complete quest for XP -> synthetic recalculation -> 기록 -> demo advance`

## Release gates

- Operational mate groups validate only with at least 30 members; the explicit ten-member synthetic demo variant is never production eligible.
- Investment judgment candidates are behavior-only; financial knowledge is behavior-only and is not an adaptation choice.
- Routine import cannot change the main goal, and active-build replacement requires explicit confirmation.
- Quest completion cannot change financial stats before synthetic recalculation.
- Production has no demo route, runtime-generated coach text, real investment, cash reward, public ranking, email verification, or password recovery.
