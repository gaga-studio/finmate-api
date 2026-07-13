# ADR-002: Expanded IA, explore mode and recommended-first routine adaptation

- Status: Accepted
- Date: 2026-07-13
- Supersedes: DEC-004, DEC-005, DEC-006

## Context

The original contract required a goal during onboarding, reduced mate discovery to one linear group path, and required users to compare three routine intensities. The final demo and team IA require users to defer a goal, inspect all three mate subareas, accept a recommendation quickly, view separate reviewed product information, and understand verified progress through a daily journey.

## Decision

1. Profile onboarding completes independently of goal confirmation. The resulting state is `EXPLORE_ONLY` until the user explicitly confirms a goal.
2. Explore-only users can read synthetic mate discovery. Raid, quest acceptance, routine import and personalized product information require `GOAL_ACTIVE`.
3. Mate retains `친구`, `메이트 찾기` and `비교 탐색`; the first and third are synthetic read-only fixtures in MVP, while mate finding is the functional import path.
4. Routine adaptation returns a primary recommendation and optional LIGHT/STANDARD/CHALLENGE intensity options. Comparing all three is not a prerequisite.
5. Related Hana product information is sourced from a reviewed catalog and is behaviorally and technically separate from the peer routine.
6. Record becomes a daily stepping-stone journey. Demo time progression is a deterministic server fixture available only in the demo profile.

## Consequences

- Existing vNext onboarding and adaptation wire shapes are intentionally breaking; vNext has not been externally released, so no compatibility adapter is required.
- Home, commands and errors become mode-aware.
- Contract tests must prove access gates, product side-effect isolation and exact demo arithmetic.
- The web screen inventory must distinguish MVP functional, synthetic read-only and demo-only screens.
