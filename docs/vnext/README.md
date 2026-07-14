# FinMate vNext canonical contract

FinMate vNext is an RPG-shaped financial habit product. This directory is the decision-complete contract for implementation in `gaga-studio/finmate-api` and `gaga-studio/finmate-web`.

## Team start here

팀원이 제품 방향, MVP 사용자 흐름, 화면 범위, 안전 기준과 완료 조건을 한 번에
확인하려면 [`팀 공용 제품 기획서`](01-product/team-product-plan.md)부터 읽는다.
이 계획서는 팀 공유용 진입 문서이며 새로운 규범 계층을 만들지 않는다. 모든 상세
정책과 계약의 충돌은 아래 규범 문서 우선순위로 해결한다.

현재 제품 흐름은 `기준선 진단 → 목표 확정 또는 탐색 모드 → 홈 레이드 →
퀘스트 수락 → 메이트 루틴 적용 → 일별 발판 기록`이다. 화면별 책임과 상태는
[`팀 공용 제품 기획서`](01-product/team-product-plan.md), 발표용 결정적 경로는
[`90초 시연 시나리오`](02-ux/demo-scenario-90s.md)에서 확인한다.

최종발표를 준비할 때는 [`발표 텍스트 패키지`](08-presentation/README.md)에서
10장 구성안, 주장·출처표와 6인 초기 사용성 검증 양식을 함께 사용한다. 발표
문서는 제품 정책을 새로 결정하지 않으며, 이 디렉터리의 규범 문서를 요약한다.

## Normative order

Conflicts are resolved in this order:

1. `06-api/openapi.yaml` for public HTTP shapes and examples.
2. `00-governance/decision-log.md` for binding product and architecture decisions.
3. `03-domain/domain-model.md` for invariants and lifecycle semantics.
4. `04-data/synthetic-data-import-and-disclosure.md` for the source-release,
   selective-import, disclosure and reward boundary.
5. `01-product/prd.md` and `02-ux/ia-and-user-flows.md` for user behavior.
6. `06-api/api-conventions.md` for representation rules.
7. `00-governance/requirements-traceability.md` for coverage.
8. `10-delivery/bootstrap-execution-plan.md` for the current two-repository implementation sequence.

## Delivery and validation

- [`분석 이벤트 명세`](07-quality/analytics-events.md): MVP 퍼널·KPI·가드레일과 금지 속성.
- [`QA·릴리스 체크리스트`](07-quality/qa-release-checklist.md): 계약, 상태, 모바일,
  접근성, 금융 안전과 릴리스 증거.
- [`6인 사용성 검증 양식`](08-presentation/usability-test-template.md): 진행 대본,
  과제, 판정 기준과 결과표.
- [`배포·복구 절차`](09-operations/deployment-and-rollback.md): 환경 경계,
  production demo 차단, 중단·복구 기준.
- [`에셋 라이선스 게이트`](09-operations/asset-license-gate.md): 폰트·캐릭터·배경의
  production 승인 조건.
- [`90초 시연 녹화 절차`](09-operations/demo-recording-runbook.md): 동결 manifest,
  영상 규격과 장면별 검수.

The expanded mate IA, explore-before-goal mode, recommendation-first routine adaptation,
read-only Hana product information, and stepping-stone record journey are bound by
[`ADR-002`](05-architecture/adr-002-expanded-ia-and-explore-mode.md).

Superseded pre-RPG material has been removed from this navigation and preserved only under `docs/legacy/finmate-vnext-pre-rpg/`, whose contents are explicitly archived and non-normative.

## Release boundary

- Backend: Java 21, Spring Boot, PostgreSQL in `gaga-studio/finmate-api`.
- Web: React, TypeScript, Vite PWA in `gaga-studio/finmate-web`.
- Navigation tabs: `홈`, `메이트`, `퀘스트`, `기록`.
- Authentication: email and password, 15-minute bearer access tokens, and a rotating opaque refresh token held only in the HttpOnly `finmate_refresh` cookie.
- Financial data: `SYNTHETIC` MyData only.
- Synthetic source: locked `gaga-studio/finmate-data` `v1.0.0`; transformed by a
  field allowlist and never committed as a raw bundle.
- Financial SNS: private by default, exact values only by granular explicit consent,
  with identifiers and raw transaction text permanently excluded.
- Coach text: `DETERMINISTIC_APPROVED_COPY` only; there is no LLM or other generative runtime.

Run both contract checks from the repository root:

```bash
.venv/bin/python docs/vnext/06-api/verify_contracts.py
.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py
```
