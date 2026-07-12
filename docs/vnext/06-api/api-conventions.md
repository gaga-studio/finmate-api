# FinMate vNext API conventions

## Base and media types

- Production base URL: `/api/v1`.
- JSON success content: `application/json`.
- Error content: `application/problem+json` following RFC 7807.
- Bearer access tokens protect every operation except signup, login, and refresh. Access tokens expire after 15 minutes.
- Signup requires `email`, a 12–72 character `password`, and `displayName`.
- `AuthSession` contains `accessToken`, `tokenType = Bearer`, `expiresAt`, and nested `user`; it never contains a refresh token.
- Signup, login, and refresh set/rotate opaque `finmate_refresh` with HttpOnly, SameSite=Lax, Path=/api/v1/auth, and 30-day lifetime. Refresh consumes that cookie with no JSON body; logout revokes the session and clears the same cookie.
- Mutation retries use `Idempotency-Key` where declared.

## Representation rules

- KRW amounts are JSON integers using `KrwAmount` (`int64`).
- Ratios and progress are integer `BasisPoints` in `0..10000`.
- Timestamps use ISO 8601 `date-time`; calendar dates use `date`; target month uses `YYYY-MM`.
- Calculated reads require `calculationVersion`, `dataState`, and `lastSyncedAt`.
- `lastSyncedAt` may be null only when no usable synthetic sync exists.

## Data states

| State | Meaning | Command behavior |
| --- | --- | --- |
| `FRESH` | Latest accepted synthetic data was calculated. | Quantitative commands may proceed. |
| `PENDING` | A sync/recalculation is in progress. | Preserve prior values; do not invent progress. |
| `STALE` | Last accepted data is outside the freshness window. | Reads remain visible; blocked commands return `DATA_STALE`. |
| `INSUFFICIENT` | Required source periods or classifications are absent. | Quantitative actions are disabled; blocked commands return `DATA_INSUFFICIENT`. |

## Errors

`Problem` requires `type`, `title`, `status`, `detail`, `instance`, `code`, and `traceId`. Validation failures include `fieldErrors`. Key codes include `VALIDATION_FAILED`, `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `DUPLICATE_EMAIL`, `NOT_FOUND`, `ACTIVE_MAIN_GOAL_EXISTS`, `DATA_STALE`, `DATA_INSUFFICIENT`, `ACTIVE_ROUTINE_BUILD_EXISTS`, `ADAPTATION_DOMAIN_REQUIRED`, and `DEMO_PROFILE_REQUIRED`. Invalid goal/report input uses `VALIDATION_FAILED`; missing goal/report resources use generic `NOT_FOUND`.

## Lifecycle rules

- Onboarding confirms the main `UserGoal`; routine endpoints never replace it.
- Mate path order is group, anonymous adventurer, routine.
- Adaptation domain is one of spending, saving, or investment judgment. A ready response has required `light`, `standard`, and `challenge` properties with matching difficulty constants. Candidate `oneOf` branches make amount, ratio, and behavior targets mutually exclusive; investment judgment can validate only as behavior with a required behavior target.
- Import creates a global active routine build. Replacement requires a body with `confirmReplacement: true`; the response identifies both archived and active builds.
- Quest completion reports XP/internal rewards. A later synthetic MyData recalculation is the only path that changes financial stats.
- Demo advancement is `POST /api/v1/demo/timeline/advance` and is absent outside the `demo` profile.

The OpenAPI operation examples are backed by JSON files in `examples/`; those files are fixture contracts, not illustrative pseudocode.
