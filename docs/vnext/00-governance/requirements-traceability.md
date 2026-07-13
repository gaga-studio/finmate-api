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
| RQ-017 Locked selective dataset import | DEC-022; data policy sections 1–5 | Dataset-release metadata is internal; public reads expose calculated metadata | SHA/schema/date/count checks, field allowlist, golden metric comparison and idempotent upsert tests pass. |
| RQ-018 Granular financial disclosure | DEC-023; disclosure policy | Disclosure read/preview/update/withdraw and public-profile schemas | Default private, preview before update, permanent exclusions and immediate recommendation removal are tested. |
| RQ-019 Information-only exact holdings | DEC-024 | `PublicFinancialProfile` separates assets/products/holdings/trades from routine operations | Product/ticker/trade reads create no routine, quest, XP, point or raid side effect. |
| RQ-020 Cosmetic-only points | DEC-025 | Point ledger, cosmetic catalog and purchase operations | Fixed catalog only; no coupon, cash, transfer, random box, report lock or investment-linked award exists. |
| RQ-021 Read-only social fixtures | DEC-026 | Friend overview/feed/streak GET operations only | No friend, follow, bookmark or feed mutation is present in the OpenAPI or runtime routes. |
| RQ-022 Split bundle/L3 provenance | DEC-027; data policy sections 1–5 | Dataset provenance remains internal; friend-group reads distinguish total and scored members | Only the locked bundle archive, exact 31-file L3 tree and 27 deterministic export files are accepted; legacy/modified payloads are rejected, stale runtime L3 is replaced, and unlocked groups with missing averages or invalid scored counts are rejected. |
