# FinMate vNext decision log

All active entries are locked for the first release. Superseded decisions remain for history and are not normative.

| ID | Status | Decision |
| --- | --- | --- |
| DEC-001 | Active | The repositories are `gaga-studio/finmate-api` and `gaga-studio/finmate-web`. |
| DEC-002 | Active | Backend is Java 21, Spring Boot and PostgreSQL; web is React, TypeScript and Vite PWA. |
| DEC-003 | Active | Bottom navigation is exactly `홈 · 메이트 · 퀘스트 · 기록`. |
| DEC-004 | Superseded by DEC-016 | Onboarding requires exactly one confirmed main goal. |
| DEC-005 | Superseded by DEC-017 | Mate discovery is limited to `group -> adventurer -> routine`. |
| DEC-006 | Superseded by DEC-018 | Users must compare exactly three adaptation candidates before import. |
| DEC-007 | Active | Spending and saving may be quantitative; investment judgment and financial knowledge are behavior-only. |
| DEC-008 | Active | One active routine build is allowed; replacement explicitly archives the previous build and activates the new one atomically. |
| DEC-009 | Active | Quest actions grant XP/internal rewards only; financial stats and raid progress change only after verified synthetic MyData recalculation. |
| DEC-010 | Active | First-release auth is email/password with short bearer access token and rotating opaque HttpOnly refresh cookie. Financial and coach providers are synthetic/deterministic. |
| DEC-011 | Active | Email verification, recovery, runtime generative AI, real investment, cash rewards, public ranking and production demo controls are excluded. |
| DEC-012 | Active | Demo advancement exists only at the demo-profile operation and only for synthetic users. |
| DEC-013 | Active | Demo goal is Europe travel: 2M current, 5M target, January 2027. |
| DEC-014 | Active | Money is integer KRW, ratios are basis points, times are ISO 8601, and calculated reads expose calculation metadata. |
| DEC-015 | Active | Errors use RFC 7807; stale and insufficient data are explicit states and blocking problem codes. |
| DEC-016 | Active | Onboarding profile completion permits `EXPLORE_ONLY`; a separate goal-confirmation command moves the user to `GOAL_ACTIVE`. Explore-only users may read mate discovery but goal-dependent commands return `GOAL_REQUIRED`. |
| DEC-017 | Active | Mate contains `친구 · 메이트 찾기 · 비교 탐색`. Friend and direct comparison are synthetic read-only in MVP; the functional path is mate group→adventurer→report→routine. |
| DEC-018 | Active | Routine adaptation presents one recommended candidate first. LIGHT/STANDARD/CHALLENGE remain optional intensity choices and need not all be compared. |
| DEC-019 | Active | The home exposes four reports: spending defense, saving HP, investment judgment and quest XP. Investment judgment contains no return or trade content. |
| DEC-020 | Active | Related Hana product information comes from a reviewed catalog, is separate from peer routines, has no application flow, and never affects product progress. |
| DEC-021 | Active | Record is a monthly daily stepping-stone journey with a daily-detail bottom sheet. The deterministic demo advances July→January with six verified 500k savings events. |

The supersession rationale and compatibility impact are recorded in ADR-002. Any future change to an active decision requires an ADR, OpenAPI update, traceability update and contract-test change in the same review.
