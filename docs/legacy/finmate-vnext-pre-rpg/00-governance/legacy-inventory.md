# 레거시 자산 인벤토리

> 문서 상태: Review
> 최종 검토: 2026-07-12
> 담당: vNext 전환 팀

## 목적

이 문서는 전면 재개발에서 기존 자산을 어떻게 다룰지 결정한다. 분류는 삭제 지시가 아니다. 새 계약과 핵심 E2E가 통과하기 전까지 기존 코드와 문서는 유지하며, vNext 구현은 이 문서가 가리키는 증거를 참고하되 기존 인터페이스를 그대로 승계하지 않는다.

## 문서 정본 우선순위

1. `docs/vnext/**`가 vNext 제품·도메인·데이터·API·품질 계약의 유일한 정본이다. 같은 주제에서 충돌하면 이 경로의 `Approved` 또는 `Accepted` 문서를 따른다.
2. 이 경로 밖의 현재 제품 문서, 기존 OpenAPI와 legacy contracts는 실행 가능한 회귀·비교 **증거 전용**이다. vNext 런타임 계약을 정의하거나 덮어쓸 수 없다.
3. 외부 로컬 입력은 Git clone·CI에서 재현되지 않을 수 있으므로 정본·필수 링크·필수 검증 입력으로 쓰지 않는다. 필요한 결정은 검토 후 `docs/vnext/**`에 재명세한다.

## 분류 기준

| 분류 | 의미 | 전환 중 조치 |
| --- | --- | --- |
| `Keep` | 현재 동작, 테스트, 합성 데이터 또는 운영 지식을 계속 증거로 쓴다. | 원본을 유지하고 회귀 검증에 사용한다. |
| `Rewrite` | 사용자 가치 또는 데이터 의미는 유지하지만 vNext 계약으로 다시 설계한다. | 새 문서와 `/api/v1` 계약을 기준으로 재구현한다. |
| `Drop` | MVP 가치 또는 안전 기준과 맞지 않아 새 제품에 넣지 않는다. | 삭제하지 않고 제외 사유와 회귀 확인 대상으로 남긴다. |
| `Archive` | 발표, 실험, 과거 의사결정의 참고 자료다. | 구현 기준으로 사용하지 않고 출처로만 보관한다. |

## 자산별 결정

| 자산 | 현재 역할 또는 관찰 | 분류 | vNext 처리와 완료 조건 |
| --- | --- | --- | --- |
| `docs/product/current-mvp.md` | 현 제품형 MVP의 기능과 미구현 범위를 설명한다. | `Rewrite` | 제품 범위의 비교 근거로만 사용한다. 새 MVP 범위는 `01-product/prd.md`가 확정한다. |
| `contracts/openapi/finmate-product-mvp.yaml` | 현재 `/api` 화면 조립형 계약이다. | `Rewrite` | 경로와 응답을 승계하지 않는다. `06-api/openapi.yaml`에 자원 중심 `/api/v1` 계약과 예시를 새로 작성한다. |
| `docs/architecture/core-loop-route-contract.md` | 기존 홈·비교·미션·기록 라우트의 연결 근거다. | `Keep` | 현재 계약의 회귀 참조로 유지한다. **vNext 런타임 계약이 아니다**; vNext 구현은 `docs/vnext/**` 정본과 `06-api/openapi.yaml`만 따른다. |
| `docs/architecture/repository-map.md` | 현재 저장소와 실행 경로의 안내다. | `Keep` | 런타임 탐색과 전환 계획의 사실 근거로 유지한다. 새 저장소 결정은 ADR-001에서 별도로 기록한다. |
| `docs/architecture/intelligence.md` | 현재 AI/검색 서비스 경계 설명이다. | `Rewrite` | AI는 후보 순위화와 설명으로 제한한다. 목표 수치 생성 권한을 승계하지 않는다. |
| `docs/architecture/synthetic-data-provider.md` | 합성 금융 데이터 제공 방식의 근거다. | `Keep` | 실제 마이데이터 전까지 어댑터와 골든 테스트의 입력 근거로 유지한다. |
| `docs/qa/testing.md` | 현재 백엔드·계약·합성 데이터 검증 명령을 제공한다. | `Keep` | 기존 동작 보존 검증에 계속 사용한다. vNext 품질 게이트는 `09-quality`에서 추가한다. |
| `apps/api/**` 및 Flyway 마이그레이션 | Spring API, 인증, 현재 사용자 상태와 저장 구조다. | `Keep` | 실행 가능한 회귀 기준이다. vNext 도메인 모델과 스키마는 기존 테이블에 종속되지 않고 새 데이터 사전으로 설계한다. |
| `apps/intelligence/**` | 현재 검색·검증·fallback 코치 구현이다. | `Keep` | 구조화 AI 출력과 fallback의 운영 근거로 유지한다. vNext AI 계약과 감사 로그에 맞게 어댑터를 다시 연결한다. |
| `fixtures/mydata-samples/**`, `fixtures/dataset-manifests/**` | 합성 거래와 데이터셋 출처·정규화 자료다. | `Keep` | vNext MyData 어댑터와 정상·경계 시나리오의 입력으로 재사용한다. 개인정보가 포함된 새 원본 데이터는 추가하지 않는다. |
| `fixtures/app-seed/README.md` | 현재 app-seed fixture의 용도와 적재 방법을 설명한다. | `Keep` | 기존 fixture를 재현·회귀 검증할 때만 사용한다. vNext fixture 계약은 `docs/vnext/04-data`와 `06-api`가 정한다. |
| `fixtures/app-seed/mission-templates.json` | 포인트 보상형 현재 미션 템플릿이다. | `Rewrite` | 목표 템플릿의 식별자 참고만 허용한다. vNext 카탈로그에는 포인트, 투자 유도, 자기확인만의 완료를 넣지 않는다. |
| `fixtures/app-seed/app-experience.json` | 5탭 기존 화면의 샘플 응답이다. | `Archive` | 화면 조립 방식의 참고 자료다. 친구 피드, 생일펀드, 포인트 화면은 vNext MVP에 이식하지 않는다. |
| `fixtures/app-seed/feature-vectors.json` | 현재 추천·분석에 쓰인 파생 특징 샘플이다. | `Archive` | 기존 모델 입력의 관찰 근거로만 보관한다. vNext 계산·공개 카드 입력으로 직접 재사용하지 않는다. |
| `fixtures/app-seed/mydata-connections.json`, `fixtures/app-seed/privacy-settings.json` | 동의 및 연결 상태의 기존 샘플이다. | `Rewrite` | 동의 버전, 범위, 철회 시각의 개념은 유지한다. 익명 카드 공개와 추천 인덱스 철회 규칙은 vNext 안전 명세로 다시 정의한다. |
| `fixtures/app-seed/onboarding-diagnoses.json`, `fixtures/app-seed/onboarding-sessions.json`, `fixtures/app-seed/personas.json`, `fixtures/app-seed/users.json` | 현재 온보딩·진단·사용자 샘플이다. | `Rewrite` | 정상·경계 입력의 회귀 근거로만 사용한다. vNext 온보딩·기준선·동의 schema로 변환하고 식별자·필드는 승계하지 않는다. |
| `fixtures/app-seed/portfolios.json` | 현재 투자 보유·포트폴리오 샘플이다. | `Drop` | 투자금·수익률·종목 보유를 목표·퀘스트·보상 입력으로 이식하지 않는다. 필요하면 비금전적 투자 판단 시나리오를 새 합성 fixture로 만든다. |
| `tools/scripts/validate_app_contract.py`, `validate_product_mvp.py` | 현재 계약·런타임 보호 검증이다. | `Keep` | 문서 작업이 현재 런타임을 손상하지 않았는지 확인한다. vNext 문서 검증기는 별도 스크립트로 만든다. |
| `tools/scripts/import-synthetic-mydata.py`, `restore-synthetic-mydata.py` | 개발용 합성 데이터 적재·복원 도구다. | `Keep` | 어댑터 통합 및 E2E의 재현 가능한 데이터 준비 절차로 유지한다. |
| 친구, 팔로잉, 생일펀드, 포인트 지갑·거래 코드와 API | 현재 제품의 사회·보상 기능이다. | `Drop` | vNext MVP에서 구현하거나 API로 노출하지 않는다. Phase 2 검토 전까지 회귀 기준으로만 남긴다. |
| 현재 미션의 포인트 보상과 투자·지출 절감 유도 문구 | 포인트 적립과 단기 성과를 중심으로 한 행동 유도다. | `Drop` | 현금성·포인트·쿠폰·투자 거래 보상을 새 목표·퀘스트·레이드에서 금지한다. |
| 외부 로컬 입력 `docs/design/_codex-current-chat/**` | 이 checkout에는 존재하지 않고 Git 추적 대상도 아니다. 검토 시점에는 별도 작업 사본 `/Users/sungjh/Projects/organization/finmate/docs/design/`에 untracked로만 있었으며, clone·CI·다른 작업 사본에서의 가용성은 보장되지 않는다. | `Archive` | 저장소 자산이나 필수 입력이라고 주장하지 않는다. 화면·계산·발표의 구현 기준, 로컬 링크, 검증 입력으로 사용하지 않으며, 채택할 결정만 `docs/vnext/**` 정본에 다시 기록한다. |

## 전환 규칙

1. `Keep` 자산은 이름이나 경로를 바꾸기 전에 기존 검증 명령의 통과를 기록한다.
2. `Rewrite` 자산은 새 문서에 요구사항 ID와 이전 자산의 차이를 명시한다. 기존 API를 그대로 복제하는 것으로 완료 처리하지 않는다.
3. `Drop` 자산은 새 OpenAPI, 목표 카탈로그, 화면 명세, 이벤트 명세, 골든 테스트에 등장하지 않아야 한다.
4. `Archive` 자산의 수치나 화면은 근거 검증 없이 새 발표·제품 주장에 재사용하지 않는다.
5. 기존 자산을 삭제하거나 비활성화하려면 `06-api` 계약, `09-quality` 핵심 E2E, 현 런타임 회귀 검증이 모두 통과했다는 별도 변경 기록이 필요하다.

## 전환 완료 판정

다음 네 조건을 모두 만족할 때만 해당 `Rewrite` 또는 `Drop` 항목의 구현 전환이 끝난 것으로 본다.

- 관련 요구사항이 [요구사항 추적표](requirements-traceability.md)에 `화면`, `API`, `데이터`, `테스트`까지 연결되어 있다.
- 새 계약이 기존 계약과 혼용되지 않으며, 클라이언트 mock과 서버 구현이 같은 예시를 사용한다.
- `Drop` 대상 기능이 MVP 화면, API, 이벤트, 보상 규칙에 없는 것을 자동 검증한다.
- 기존 제품 검증과 vNext 핵심 E2E의 결과가 릴리스 기록에 남아 있다.
