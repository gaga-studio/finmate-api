# FinMate vNext bootstrap execution plan

## Global constraints

- Existing FinMate frontend and backend repositories are read-only references.
- `finmate-api` owns product documentation and the OpenAPI contract.
- `finmate-web` consumes a generated TypeScript client and never calculates financial metrics.
- The first release uses synthetic MyData, deterministic coach copy and internal-only rewards.
- The main goal is confirmed during onboarding. Mate imports one active routine build without replacing the main goal.
- The demo timeline endpoint exists only in the `demo` Spring profile.

## Tasks

1. Bootstrap both repositories, CI, local development and handoff policy.
2. Align vNext documentation and OpenAPI with the RPG product contract.
3. Implement authentication, onboarding and synthetic financial baseline.
4. Implement goal, routine, raid, quest, daily record and demo timeline APIs.
5. Build the React PWA shell and complete the representative mock flow.
6. Replace mocks with the generated API client and run the same E2E flow.
7. Verify local/offline execution and publish bootstrap branches.

## Representative flow

`signup -> onboarding -> goal -> home raid -> animal report -> mate routine -> quest -> record -> demo advance -> goal complete`
