# ADR-001: vNext stack and repository boundaries

Status: Accepted
Date: 2026-07-13

## Context

FinMate vNext needs independently deployable web and API applications with one reviewed HTTP contract. Product state is transactional and the first release uses deterministic adapters.

## Decision

| Boundary | Decision |
| --- | --- |
| API repository | `gaga-studio/finmate-api` |
| Web repository | `gaga-studio/finmate-web` |
| API runtime | Java 21 and Spring Boot |
| Persistence | PostgreSQL with migrations owned by the API repository |
| Web runtime | React, TypeScript, Vite PWA |
| Public contract | `docs/vnext/06-api/openapi.yaml` in the API repository |
| Authentication | Email/password sessions |
| Financial provider | Versioned `SYNTHETIC` MyData adapter |
| Coach content | Versioned `DETERMINISTIC_APPROVED_COPY` catalog |

The API is a modular monolith for auth, onboarding/goal, mate, routine adaptation/build, raid/reporting, quest, record, and demo modules. Commands crossing goal/build/quest boundaries use PostgreSQL transactions. The web repository generates or validates TypeScript clients against the published OpenAPI contract.

The `demo` Spring profile conditionally registers `POST /api/v1/demo/timeline/advance`; production profiles do not register the controller or route. Demo writes use isolated synthetic fixture identities.

## Rejected for this release

- A monorepo, a third runtime repository, and independently deployed microservices.
- Runtime text generation or an external model service.
- Real MyData credentials or brokerage integration.
- Public ranking, cash rewards, email verification, and password recovery.

## Consequences

- Contract changes land in the API repository before dependent web changes.
- Integer KRW and basis-point rules are shared through generated types and tests.
- Deterministic adapters make onboarding, stale/insufficient states, recalculation, and demo advancement reproducible.
- Replacing a routine build is a server transaction and cannot be implemented as a web-only overwrite.

## Compliance

A change is compliant only when OpenAPI validation, mock-spec unit tests, requirement traceability, and fixture examples pass together. No superseded repository, runtime, social-room, future-planning, or routine-derived-goal assumption is an active decision.
