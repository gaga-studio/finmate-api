# FinMate vNext canonical contract

FinMate vNext is an RPG-shaped financial habit product. This directory is the decision-complete contract for implementation in `gaga-studio/finmate-api` and `gaga-studio/finmate-web`.

## Normative order

Conflicts are resolved in this order:

1. `06-api/openapi.yaml` for public HTTP shapes and examples.
2. `00-governance/decision-log.md` for binding product and architecture decisions.
3. `03-domain/domain-model.md` for invariants and lifecycle semantics.
4. `01-product/prd.md` and `02-ux/ia-and-user-flows.md` for user behavior.
5. `06-api/api-conventions.md` for representation rules.
6. `00-governance/requirements-traceability.md` for coverage.

Other files under `docs/vnext/**` are supporting research, delivery material, or historical drafts. They are non-normative and cannot add a feature, route, domain state, repository, runtime, or product assumption not present in the sources above.

## Release boundary

- Backend: Java 21, Spring Boot, PostgreSQL in `gaga-studio/finmate-api`.
- Web: React, TypeScript, Vite PWA in `gaga-studio/finmate-web`.
- Navigation tabs: `홈`, `메이트`, `퀘스트`, `기록`.
- Authentication: email and password.
- Financial data: `SYNTHETIC` MyData only.
- Coach text: `DETERMINISTIC_APPROVED_COPY` only; there is no LLM or other generative runtime.

Run both contract checks from the repository root:

```bash
.venv/bin/python docs/vnext/06-api/verify_contracts.py
.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py
```
