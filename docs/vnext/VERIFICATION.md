# FinMate vNext verification record

## Current contract

- Verification date: 2026-07-13
- Branch: `codex/bootstrap-vnext`
- OpenAPI operations: 25
- OpenAPI schemas: 44
- Referenced JSON examples: 17
- Canonical entrypoint: `docs/vnext/README.md`
- Archived pre-RPG package: `docs/legacy/finmate-vnext-pre-rpg/README.md`

Only the files linked by the canonical entrypoint are current. The legacy package is excluded from current navigation and verification claims.

## Required verification

| Command | Result |
| --- | --- |
| `.venv/bin/python docs/vnext/06-api/verify_contracts.py` | `CONTRACT_VERIFICATION_OK operations=25 schemas=44 examples=17 structuralChecks=19 authChecks=14 goalChecks=5` |
| `.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py` | `Ran 6 tests`; `OK` |

The structural checks execute positive and negative OpenAPI schema validation for routine candidate target/domain combinations, exact LIGHT/STANDARD/CHALLENGE slots, selected-domain binding across all three adaptation variants, and operational versus synthetic-demo group variants. Auth checks require signup display name and password bounds, nested access-token session JSON without a refresh token, cookie-only refresh/logout, rotating or clearing `Set-Cookie` response headers with executable attribute/lifetime metadata, implementation problem codes, and complete RFC 7807 metadata. Goal checks require the current amount, a 255-character title bound, `ACTIVE_MAIN_GOAL_EXISTS`, and generic validation/not-found codes.

## Scope

These results validate the documentation and HTTP contract. Backend runtime verification is owned by the implementation task and is not claimed here.
