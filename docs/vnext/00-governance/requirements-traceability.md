# Requirements traceability

| Requirement | Decision/domain evidence | OpenAPI evidence | Acceptance evidence |
| --- | --- | --- | --- |
| RQ-001 Email/password authentication | DEC-010 | Auth operations and session schemas | Refresh token remains cookie-only; no verification or recovery route. |
| RQ-002 Optional goal after onboarding | DEC-016; ONB-1..2, UG-1..3 | `OnboardingState`, goal confirmation, explore home | Explore-only can read mate; goal-dependent commands return `GOAL_REQUIRED` 409. |
| RQ-003 Four-tab product | DEC-003 | Home, mate, quest and record tags | IA contains exactly four bottom tabs. |
| RQ-004 Expanded mate IA | DEC-017; MATE-1..3 | Friend overview/feed, group report, explore search, adventurer report | Friend/direct compare are synthetic read-only; core group path is traversable. |
| RQ-005 Privacy threshold | MATE-1..2 | Group sample and synthetic-demo fields | Production aggregation requires 30+ and exposes no forbidden peer fields. |
| RQ-006 Recommended-first adaptation | DEC-018; RR-1..2 | `RoutineRecommendation`, `recommendedCandidate`, `intensityOptions` | Recommendation is primary; exactly three optional unique intensity options remain available. |
| RQ-007 Quantitative safety | DEC-007; RR-1 | Candidate target branches | Investment judgment is behavior-only; no product, holding, return or trade target. |
| RQ-008 One active routine | DEC-008; ARB-1..3 | Import, active build and replacement operations | Import requires active goal; replacement confirmation is atomic. |
| RQ-009 Quest acceptance and stat separation | DEC-009; Q-1..5 | Quest accept/complete schemas | Accept is explicit; quest actions do not alter financial progress. |
| RQ-010 Four character reports | DEC-019 | Character-report operation and four-type enum | Each report has reason/trend/action; rabbit has no investment-performance content. |
| RQ-011 Related product separation | DEC-020 | `RelatedHanaProductInfo` read operation | `affectsProgress=false`, no application operation, no progress side effects. |
| RQ-012 Daily stepping journey | DEC-021; DailyJourneyMonth | Monthly journey and daily record operations | Date order, summary/detail reconciliation and node density rules pass. |
| RQ-013 Demo fixture | DEC-012..13, DEC-021 | Demo timeline frames | August–January six 500k events move exactly 2M→5M and complete the raid. |
| RQ-014 Calculated metadata | DEC-014 | All calculated read schemas | Calculation version, data state and last sync are required. |
| RQ-015 Error and data-state handling | DEC-015 | RFC 7807, `FRESH/PENDING/STALE/INSUFFICIENT` | Blocked commands and stale/insufficient reads are explicit. |
| RQ-016 Explicit exclusions | DEC-011, DEC-020 | No forbidden operations | No ranking, cash reward, real trade, runtime LLM, product signup or production demo route. |
