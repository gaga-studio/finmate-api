# FinMate vNext canonical contract

FinMate vNext is an RPG-shaped financial habit product. This directory is the decision-complete contract for implementation in `gaga-studio/finmate-api` and `gaga-studio/finmate-web`.

## Team start here

팀원이 제품 방향, MVP 사용자 흐름, 화면 범위, 안전 기준과 완료 조건을 한 번에
확인하려면 [`팀 공용 제품 기획서`](01-product/team-product-plan.md)부터 읽는다.
이 계획서는 팀 공유용 진입 문서이며 새로운 규범 계층을 만들지 않는다. 모든 상세
정책과 계약의 충돌은 아래 규범 문서 우선순위로 해결한다.

## Normative order

Conflicts are resolved in this order:

1. `06-api/openapi.yaml` for public HTTP shapes and examples.
2. `00-governance/decision-log.md` for binding product and architecture decisions.
3. `03-domain/domain-model.md` for invariants and lifecycle semantics.
4. `01-product/prd.md` and `02-ux/ia-and-user-flows.md` for user behavior.
5. `06-api/api-conventions.md` for representation rules.
6. `00-governance/requirements-traceability.md` for coverage.
7. `10-delivery/bootstrap-execution-plan.md` for the current two-repository implementation sequence.

Superseded pre-RPG material has been removed from this navigation and preserved only under `docs/legacy/finmate-vnext-pre-rpg/`, whose contents are explicitly archived and non-normative.

## Release boundary

- Backend: Java 21, Spring Boot, PostgreSQL in `gaga-studio/finmate-api`.
- Web: React, TypeScript, Vite PWA in `gaga-studio/finmate-web`.
- Navigation tabs: `홈`, `메이트`, `퀘스트`, `기록`.
- Authentication: email and password, 15-minute bearer access tokens, and a rotating opaque refresh token held only in the HttpOnly `finmate_refresh` cookie.
- Financial data: `SYNTHETIC` MyData only.
- Coach text: `DETERMINISTIC_APPROVED_COPY` only; there is no LLM or other generative runtime.

Run both contract checks from the repository root:

```bash
.venv/bin/python docs/vnext/06-api/verify_contracts.py
.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py
```
