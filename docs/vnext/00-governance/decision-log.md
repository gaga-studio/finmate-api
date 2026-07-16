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
| DEC-022 | Active | Synthetic source data is locked to `gaga-studio/finmate-data` `v1.0.0` and its SHA-256. All 31 L3 tables are classified as 16 runtime allowlisted, 5 golden-only and 10 excluded; backend code recalculates financial metrics while derived L3 metrics/stats/balance/budget outputs remain DB-excluded golden oracles. |
| DEC-023 | Active | Financial SNS disclosure is private by default and independently consented by category after preview. Account numbers, raw transaction text, detailed employer/location, authentication identifiers and source-user identity are permanently excluded; withdrawal immediately removes public and recommendation views, including an owner-linked synthetic profile projection. |
| DEC-024 | Active | Consented exact products, holdings and trades are read-only information. Routine adaptation, quests, rewards and raid progress may use only reviewed habit abstractions and never product signup, ticker purchase or trade timing. |
| DEC-025 | Active | Quest XP and fixed internal points are separate. Points purchase deterministic cosmetics only; coupons, cash conversion, transfer, random boxes, report locks and investment-linked rewards are excluded. |
| DEC-026 | Active | Synthetic friend status, amount-free feed and shared streaks are read-only in MVP; no friend, follow, bookmark or feed-write command is exposed. |
| DEC-027 | Active | The immutable `v1.0.0` L1/L2 bundle and corrected L3 parquet tree have independent provenance. The importer requires bundle commit `63ca3d0`, L3 commit `eab7f87`, the locked 31-file L3 tree digest and 27 locked export-file checksums; a legacy single-source manifest or any older/modified payload is rejected. Runtime L3 is replaced as one release snapshot, and API-owned goal templates are not imported from the data repository. The v2 segmentation excludes `subscription_count`, creates 11 balanced product partitions, and does not rebuild the frozen friendship graph. |

The supersession rationale and compatibility impact are recorded in ADR-002. Any future change to an active decision requires an ADR, OpenAPI update, traceability update and contract-test change in the same review.
