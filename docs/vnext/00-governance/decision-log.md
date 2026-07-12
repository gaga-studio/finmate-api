# FinMate vNext decision log

All entries are locked for the first release.

| ID | Decision |
| --- | --- |
| DEC-001 | The repositories are `gaga-studio/finmate-api` and `gaga-studio/finmate-web`. |
| DEC-002 | The backend is Java 21, Spring Boot, and PostgreSQL. The web app is React, TypeScript, and Vite PWA. |
| DEC-003 | The four tabs are `홈`, `메이트`, `퀘스트`, `기록`. |
| DEC-004 | During onboarding the user explicitly confirms exactly one main `UserGoal`. A routine import never creates, mutates, or replaces that goal. |
| DEC-005 | Mate discovery is ordered `group -> anonymous adventurer -> routine`. An operational anonymous group requires at least 30 members. The fixture group marked `syntheticDemo = true` may contain exactly 10 members and is never eligible for production aggregation. |
| DEC-006 | Routine adaptation first selects exactly one domain: `SPENDING`, `SAVING`, or `INVESTMENT_JUDGMENT`. It then returns `LIGHT`, `STANDARD`, and `CHALLENGE` candidates. |
| DEC-007 | Spending and saving candidates may be quantitative. Investment judgment and financial knowledge are behavior-only. Amounts cannot appear as investment outcomes, investment targets, or knowledge targets. |
| DEC-008 | A routine import creates the one global `ActiveRoutineBuild`. If one exists, the API returns a conflict until the user explicitly confirms replacement. Replacement archives the old build and activates the new build atomically. |
| DEC-009 | Quest completion grants XP and approved internal rewards only. Spending, saving, and investment-judgment financial stats change only after a synthetic MyData recalculation. |
| DEC-010 | First-release auth is email/password. Financial data is `SYNTHETIC`; coach copy is `DETERMINISTIC_APPROVED_COPY`. |
| DEC-011 | Email verification, password recovery, generative runtime, real investment, cash rewards, public ranking, and production demo controls are out of scope. |
| DEC-012 | Demo advancement is only `POST /api/v1/demo/timeline/advance` and the server must expose it only when the Spring `demo` profile is active. |
| DEC-013 | The representative fixture is a Europe travel goal with `currentAmountKrw = 2000000`, `targetAmountKrw = 5000000`, and `targetMonth = 2027-01`. |
| DEC-014 | Money is integer KRW, ratios are basis points, timestamps are ISO 8601, and calculated reads expose `calculationVersion`, `dataState`, and `lastSyncedAt`. |
| DEC-015 | Errors use RFC 7807. Stale and insufficient data are explicit `dataState` values and, when blocking a command, explicit problem codes. |

Any change to a locked decision requires a new ADR, corresponding OpenAPI update, traceability update, and contract-test change in the same review.
