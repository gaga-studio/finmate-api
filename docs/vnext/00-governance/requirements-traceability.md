# Requirements traceability

| Requirement | Decision and domain evidence | OpenAPI evidence | Acceptance evidence |
| --- | --- | --- | --- |
| RQ-001 Email/password authentication | DEC-010; PRD 4.1 | `signUp`, `logIn`, `refreshSession`, `logOut` | Signup and login examples validate; no verification or recovery operation exists. |
| RQ-002 One onboarding goal | DEC-004; `UserGoal` invariant UG-1 | `getOnboarding`, `completeOnboarding`, `getActiveUserGoal` | A completed onboarding response contains one confirmed Europe travel goal. |
| RQ-003 Four-tab product | DEC-003; PRD 3 | Home, mate, quest, and record operation groups | IA contains exactly `홈`, `메이트`, `퀘스트`, `기록`. |
| RQ-004 Ordered mate discovery | DEC-005; RA-1 | `listMateGroups`, `listRecommendedAdventurers`, `getAdventurerRoutine` | Links and identifiers enforce group, adventurer, then routine traversal. |
| RQ-005 Group privacy threshold | DEC-005; RA-2 | `MateGroup.memberCount`, `syntheticDemo` | Production group is 30+; fixture demo group is explicitly synthetic and has 10. |
| RQ-006 Adaptation choices | DEC-006; RA-3 | `createRoutineAdaptation`, `chooseRoutineAdaptationDomain`, `RoutineAdaptationCandidate` | One domain choice yields LIGHT/STANDARD/CHALLENGE. |
| RQ-007 Quantitative safety | DEC-007; RA-4 | `AdaptationDomain`, `TargetKind`, candidate schemas | Investment judgment is behavior-only; only spending/saving may carry KRW or basis points. |
| RQ-008 One global routine build | DEC-008; ARB-1..3 | `getActiveRoutineBuild`, `importRoutineAdaptationCandidate`, `replaceActiveRoutineBuild` | Unconfirmed replacement is 409; confirmed replacement archives previous build atomically. |
| RQ-009 Quest and stat separation | DEC-009; Q-1..2 | `completeQuest`, `QuestCompletion`, `RaidView` | Completion returns XP/internal reward; calculated stats remain tied to MyData recalculation. |
| RQ-010 Calculated read metadata | DEC-014; API conventions | Home, raid, report, mate, adaptation, build, quest and record schemas | Verifier requires all three metadata fields. |
| RQ-011 Stale/insufficient handling | DEC-015 | `DataState`, `Problem`, `DataStale`, `DataInsufficient` | Read examples expose state; blocked commands use RFC 7807. |
| RQ-012 Demo fixture and control | DEC-012..13 | `advanceDemoTimeline`, `DemoTimelineView` | Endpoint is `/api/v1/demo/timeline/advance`; fixture has 2M/5M KRW and January target. |
| RQ-013 Explicit exclusions | DEC-011 | No email verification, recovery, ranking, investment execution, or cash-reward operations | Verifier checks required surface and canonical decision text. |
