# FinMate vNext verification record

## Current contract

- Verification date: 2026-07-13
- Branch: `codex/expanded-ia-contract`
- OpenAPI operations: 35
- OpenAPI schemas: 68
- Referenced JSON examples: 28
- Canonical entrypoint: `docs/vnext/README.md`
- Archived pre-RPG package: `docs/legacy/finmate-vnext-pre-rpg/README.md`

Only files linked by the canonical entrypoint are current. Legacy material remains
non-normative and is excluded from contract claims.

## Required verification

| Command | Result |
| --- | --- |
| `/Users/sungjh/Projects/finmate-api/.venv/bin/python docs/vnext/06-api/verify_contracts.py` | `CONTRACT_VERIFICATION_OK operations=35 schemas=68 examples=28 structuralChecks=30 authChecks=14 goalChecks=5` |
| `/Users/sungjh/Projects/finmate-api/.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py` | `Ran 8 tests`; `OK` |

The structural checks validate:

- goal-free `EXPLORE_ONLY` onboarding and home, including all goal-dependent locks;
- `GOAL_ACTIVE` home with a main goal and raid;
- saving/spending quantitative routines and behavior-only investment-judgment routines;
- one recommended routine plus unique `LIGHT`, `STANDARD`, and `CHALLENGE` alternatives;
- production group minimum size and explicit synthetic-demo group separation;
- read-only Hana product information with no enrollment or growth effect;
- July journey nodes in complete date order, a distinct July 9 activity, and July 11 salary as the primary activity;
- daily budget arithmetic and largest-absolute-value primary activity selection;
- six deterministic 500,000 KRW saving events from August 2026 through January 2027;
- RFC 7807 `GOAL_REQUIRED` behavior for locked commands;
- cookie-only refresh rotation and complete authentication problem metadata.

## Scope

These results validate documentation, examples, and the public HTTP contract. Runtime
backend implementation of the expanded contract and UI implementation are separate tasks
and are not claimed here.
