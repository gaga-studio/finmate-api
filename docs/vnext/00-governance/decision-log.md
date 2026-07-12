# 결정 로그

> 문서 상태: Review
> 최종 검토: 2026-07-12
> 결정 기록 단위: 제품·도메인·안전·기술 중 구현 계약에 영향을 주는 변경

## 운영 규칙

- `Locked` 결정은 후속 문서가 반드시 따른다. 변경은 새 결정으로 기록하고 이전 행을 `Superseded`로 바꾼다.
- `Pending`은 구현 착수 게이트를 통과하지 못하게 하는 미결 항목이다. 추측으로 계약을 작성하지 않는다.
- `Proposed`는 검토용이며 구현 기준이 아니다.
- 각 `Locked` 결정은 요구사항 ID, 소유자, **검증 증거**, **출처 증거**를 가진다. 검증 증거는 실행할 골든 테스트·계약 검사 또는 승인 기록을, 출처 증거는 해당 결정을 뒷받침하는 `docs/vnext` 정본 문서와 절을 기록한다. 상세 요구사항 연결은 [요구사항 추적표](requirements-traceability.md)를 기준으로 한다.

## 잠긴 결정

| ID | 상태 | 결정 | 근거와 구현 영향 | 소유자 | 연결 요구사항 | 검증 증거 | 출처 증거 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DEC-001 | Locked | `docs/vnext` 안의 Git Markdown, OpenAPI, YAML, JSON, PPTX를 vNext 정본으로 하고 Notion은 포털·진행·회의 결정만 기록한다. | `docs/vnext` 문서와 계약의 변경은 Git PR에서 검토한다. Notion에 사양 원문을 복제하지 않는다. 레거시 계약은 회귀·비교용 증거일 뿐 vNext 구현 계약이 아니다. | 제품·테크 리드 | RQ-014 | PR에서 `docs/vnext/README.md`의 정본 우선순위, OpenAPI와 연결 문서를 교차 검토하고 로컬 링크 검사를 통과한다. | `docs/vnext/README.md`의 문서 운영 원칙; `docs/vnext/05-architecture/adr-001-stack-and-repository.md`의 Contract 행. |
| DEC-002 | Locked | MVP 핵심 흐름은 `추천 모험가 → 루틴 선택 → 목표 확정 → 자동 레이드 → 퀘스트 → 기록`이다. | 모든 MVP 화면과 API는 이 흐름의 한 단계에 연결되어야 한다. | 제품 | RQ-003~RQ-010 | `E2E-MOCK-CORE-001` mock 핵심 흐름과 화면·API 추적 행의 누락 검사를 통과한다. | `docs/vnext/README.md`의 제품 기준; `docs/vnext/01-product/prd.md` 2절. |
| DEC-003 | Locked | MVP에서 메이트는 비교 탐색만 제공하며 친구와 메이트 찾기는 Phase 2다. | 친구, 팔로잉, 그룹 탐색, 순위 API와 화면을 새 계약에 넣지 않는다. | 제품 | RQ-003, RQ-013 | `SCOPE-PHASE2-001`과 `MVP-SAFETY-SURFACE-001`의 금지 경로 검사에서 소셜 경로가 0건이어야 한다. | `docs/vnext/01-product/prd.md` 7절; `docs/vnext/07-ai-safety/privacy-consent-and-threat-model.md`의 T13. |
| DEC-004 | Locked | 추천 모험가는 별도 공개 동의를 받은 실제 익명 사용자이며, 정확 금액·식별 가능한 상품·종목·희귀 태그는 노출하지 않는다. | 정확 코호트가 30명 미만이면 비금융 생활맥락만 일반화하고 금융 수용력 제약은 유지한다. 안전한 일반화 코호트가 30명 이상일 때만 `GROUP_ROUTINE`을 만들며, 없으면 `INSUFFICIENT`와 빈 목록이다. | 개인정보·데이터 | RQ-003, RQ-012 | `CARD-PUBLIC-FIELDS-001`, `CARD-GROUP-001`, `CARD-GROUP-BOUNDARY-001`, `CONSENT-WITHDRAW-001`과 OpenAPI 응답 허용 목록 검사를 통과한다. | `docs/vnext/01-product/prd.md` 4.3절; `docs/vnext/07-ai-safety/privacy-consent-and-threat-model.md` 4절. |
| DEC-005 | Locked | 목표 후보 ID와 모든 수치는 코드, 검수된 GoalTemplate, 사용자 기준선, 그룹 안전 범위로 계산한다. AI는 서버 허용 후보 ID의 추천 순위와 설명만 담당한다. | API가 AI ID·근거·수치·정책을 검증하고 불일치 출력 전체를 폐기한다. AI 실패에는 결정론적 순위를 사용하며 후보 API는 `recommendation.recommendedCandidateId`, `reasonCodes`, `summary`, `riskNotice`, `recommendationState` (`AI_GENERATED` 또는 `DETERMINISTIC_FALLBACK`)를 항상 반환한다. | 도메인·AI | RQ-004, RQ-011 | `GOAL-INCREASE-001`, `CALC-DISPOSABLE-001`, `GOAL-NO-IMPROVEMENT-001`, `GOAL-ROUNDING-001`과 `AI-VALID-001`, `AI-UNKNOWN-ID-001`, `AI-NUMBER-MUTATION-001`, `AI-FALLBACK-NO-STANDARD-001`에서 결정론적 후보·수치, 허용 ID와 fallback을 검증한다. | `docs/vnext/03-domain/calculation-policy.md` 6절; `docs/vnext/07-ai-safety/ai-coach-specification.md` 2·4.1·5절. |
| DEC-006 | Locked | MVP 목표는 예산 범위 유지, 구독 점검, 저축률 증가, 비상금 증가, 자동저축 확인, 투자 판단 점검, 금융지식 확인의 7개 템플릿으로 제한한다. | 자유 문장 목표, 상품 가입, 종목 매수, 투자금 증액, 수익률 목표를 만들지 않는다. | 제품·도메인 | RQ-004, RQ-013 | `GOAL-TEMPLATE-CATALOG-001`과 `MVP-SAFETY-SURFACE-001`에서 허용 템플릿만 존재하고 금지 계약이 없음을 확인한다. | `docs/vnext/01-product/prd.md` 4.4절; `docs/vnext/03-domain/goal-template-catalog.yaml`. |
| DEC-007 | Locked | 활성 목표는 사용자당 하나만 허용한다. 확정 시 템플릿·계산·동의 버전과 기준선 스냅샷을 고정한다. | 후보, 확정, 활성, 일시정지, 완료, 만료, 취소 상태와 멱등성 키가 API·데이터·테스트에 필요하다. | 도메인 | RQ-005, RQ-006 | `GOAL-IDEMPOTENCY-001` 중복 확정, `GOAL-ACTIVE-CONFLICT-001` 활성 목표 충돌, `GOAL-CANDIDATE-EXPIRED-001` 후보 만료 및 상태 전이 계약 테스트를 통과한다. | `docs/vnext/03-domain/state-machines.md` 2절; `docs/vnext/06-api/openapi.yaml`의 `createGoal`, `pauseGoal`, `resumeGoal`, `cancelGoal`. |
| DEC-008 | Locked | 모든 계산 응답에는 `calculationVersion`, `dataState`, `lastSyncedAt`을 포함한다. 데이터 부족·오래됨·확인 필요에는 정량 목표를 만들지 않거나 진행을 멈춘다. | 소득 또는 필수지출이 기준선 대비 20% 이상 변하면 자동 변경 대신 재설정을 요구한다. | 도메인·데이터 | RQ-001, RQ-002, RQ-006 | `ONBOARDING-BASELINE-INSUFFICIENT-001`, `DATA-STALE-001`, `MYDATA-SYNC-PARTIAL-001`, `CALC-TRANSFER-001`, `BASELINE-CHANGE-001`, `GOAL-PAUSE-001`에서 상태 표기, 갱신 중지, 재설정 전환을 검증한다. | `docs/vnext/03-domain/calculation-policy.md` 3·7·8절; `docs/vnext/06-api/api-conventions.md` 9절. |
| DEC-009 | Locked | 자동 레이드는 실제 목표 진행률의 3단계 시각화이며, 새 데이터 반영 후 현재 최고 지점에서 기다린다. | 재도전 횟수, 시간 누적 피해, 오프라인 전투 누적을 저장·표시하지 않는다. 진행률 하락은 실패·벌점·보스 회복으로 표현하지 않는다. | 제품·도메인 | RQ-007, RQ-013 | `RAID-STAGE-BOUNDARY-001` 단계 계산, `RAID-HIGH-WATERMARK-001` 진행 하락, `RAID-WAITING-DATA-001` 데이터 대기와 금지 이벤트 검사를 통과한다. | `docs/vnext/03-domain/calculation-policy.md` 8절; `docs/vnext/03-domain/state-machines.md` 3절. |
| DEC-010 | Locked | 퀘스트 XP, 금융 스탯, 목표 진행률, 레이드 연출은 별도 계산한다. | 퀘스트 완료만으로 금융 스탯·보스 피해·목표 진행률을 올리지 않는다. 검증된 실제 데이터 변화만 해당 지표를 재계산한다. | 도메인 | RQ-008, RQ-009 | `QUEST-EVIDENCE-PENDING-001`, `QUEST-NO-DOUBLE-COUNT-001`, `INVESTMENT-SAFETY-001`과 `REPORT-SPENDING-RANGE-001`, `REPORT-INVESTMENT-NONMONETARY-001`, `REPORT-NEUTRAL-COPY-001`에서 증거 검증과 지표 분리를 확인한다. | `docs/vnext/03-domain/calculation-policy.md` 5절; `docs/vnext/03-domain/state-machines.md` 4절. |
| DEC-011 | Locked | 투자는 판단·점검·학습만 다룬다. 투자금액·거래·수익률에는 XP, 포인트, 배지, 축하 효과를 지급하지 않는다. | 투자 퀘스트와 목표는 행동형이며, 투자 보상 이벤트를 차단하는 테스트가 필요하다. | 제품·안전 | RQ-008, RQ-013 | `INVESTMENT-SAFETY-001` 투자 보상·유도 0건과 `DATA-STALE-001` 데이터 지연 벌점 0건을 통과한다. | `docs/vnext/01-product/prd.md` 4.6·4.7절; `docs/vnext/07-ai-safety/privacy-consent-and-threat-model.md` 7절. |
| DEC-012 | Locked | 목표·단계 보상은 비금전적 외형, 해금, 기록 배지, 설명 연출로 제한한다. | 포인트, 쿠폰, 현금성 혜택, 금융상품 가입 보상, 확률형 보상을 MVP에서 제외한다. | 제품·안전 | RQ-007, RQ-013 | `MVP-SAFETY-SURFACE-001`의 보상·필드 부재 검사와 이벤트 택소노미 허용 목록 검사를 통과한다. | `docs/vnext/01-product/project-charter.md` 7절; `docs/vnext/08-analytics/event-taxonomy-and-kpis.md` 4절. |
| DEC-013 | Locked | 공개 철회는 versioned·idempotent·withdrawal-first 명령이며 추천 인덱스와 새 카드 생성에서 즉시 제외하고 캐시와 파생 추천은 최대 24시간 안에 제거한다. | 명령은 `consentAggregateId`, `expectedVersion`/`If-Match`, `Idempotency-Key`를 요구한다. `WITHDRAWN`은 terminal이며 stale·delayed opt-in은 `412`라 같은 aggregate를 되살릴 수 없다. 재동의는 새 ID다. | 개인정보·데이터 | RQ-012 | `CONSENT-WITHDRAW-001`의 즉시 제외·24시간 제거와 `CONSENT-RACE-001`의 withdrawal-first·stale opt-in `412`·terminal 불변을 통과한다. | `docs/vnext/07-ai-safety/privacy-consent-and-threat-model.md` 3.1·5절; `docs/vnext/05-architecture/architecture.md` 7·9절. |
| DEC-014 | Locked | 클라이언트 목표는 모바일 웹/PWA이고 MyData는 합성 fixture를 사용하는 어댑터부터 시작한다. | 실제 기관 연동을 MVP 성공 조건으로 삼지 않는다. 계약은 실제·합성 제공자를 같은 어댑터 인터페이스로 다룬다. | 아키텍처·데이터 | RQ-001, RQ-002, RQ-014 | `E2E-MOCK-CORE-001` mock 핵심 흐름, `E2E-SYNTHETIC-ADAPTER-001` synthetic adapter 흐름, `CONTRACT-OPENAPI-EXAMPLES-001` OpenAPI 예시를 통과한다. | `docs/vnext/05-architecture/adr-001-stack-and-repository.md` 2절; `docs/vnext/04-data/mydata-adapter.md`; `docs/vnext/09-quality/golden-test-cases.yaml`. |
| DEC-015 | Locked | AI 동의가 유효한 `OPERATIONAL` 로그는 최대 90일, 유효한 `INCIDENT_EVIDENCE`는 최대 180일이다. AI 동의 철회·계정 삭제 뒤 모든 non-`LEGAL_REQUIRED` 연결 기록·콘텐츠는 7일 안에 삭제 또는 비가역 분리한다. | `INCIDENT_EVIDENCE`는 철회·삭제를 넘겨 존속할 수 없고 사건 전에 필수 법적 필드와 승인 시각을 모두 갖춘 `LEGAL_REQUIRED` 재분류만 예외다. 백업은 최대 30일이며 tombstone을 복원 전에 재생한다. | 개인정보·AI·플랫폼 | RQ-011, RQ-012 | `AI-AUDIT-RETENTION-001`, `AI-CONSENT-WITHDRAW-DELETE-001`, `ACCOUNT-DELETE-001`, `BACKUP-RESTORE-001`에서 90/180/7/30일 경계, incident 삭제, legal 사전 분류, tombstone 재생을 검증한다. | `docs/vnext/07-ai-safety/privacy-consent-and-threat-model.md` 5절; `docs/vnext/07-ai-safety/ai-coach-specification.md` 7절; `docs/vnext/05-architecture/non-functional-requirements.md` 5·6절. |

## 대기 중인 ADR

| ID | 상태 | 결정 질문 | 선택 기한 | 결정자 | 완료 기준 |
| --- | --- | --- | --- | --- | --- |
| ADR-001 | Pending | vNext의 클라이언트, 서버, 데이터 저장소, 모노레포/다중 저장소 구성을 무엇으로 할 것인가? | Task 4의 `05-architecture/adr-001-stack-and-repository.md` 검토 시 | 테크 리드 | 대안, 평가 기준, 선택, 이행 영향, 로컬 실행·CI 검증 명령이 기록되어 `Approved` 상태가 된다. |

## 새 결정 추가 형식

| 필드 | 필수 내용 |
| --- | --- |
| ID | `DEC-###` 또는 `ADR-###`; 번호를 재사용하지 않는다. |
| 상태 | `Proposed`, `Locked`, `Superseded`, `Pending` 중 하나. |
| 결정 | 구현자가 예·아니오로 판단할 수 있는 문장. |
| 근거와 영향 | 왜 필요한지와 화면·API·데이터·테스트에 생기는 의무. |
| 소유자 | 최종 승인 역할. |
| 연결 요구사항 | [요구사항 추적표](requirements-traceability.md)의 하나 이상 ID. |
| 검증 증거 | 실행할 골든 테스트·계약 검사 또는 승인 기록의 ID와 성공 조건. 아직 실행 전이면 계획된 검증임을 명시한다. |
| 출처 증거 | 결정을 뒷받침하는 `docs/vnext` 정본 경로와 절, 또는 승인된 외부 근거의 식별자. 레거시 문서는 증거 전용으로만 표시한다. |

## 변경 절차

1. 변경 제안자는 영향받는 요구사항과 기존 결정을 기록한다.
2. 제품, 도메인, 안전, 아키텍처 중 영향받는 소유자가 합의한다.
3. 새 행을 추가하고 이전 행은 `Superseded`로 변경한다. 기존 행을 조용히 덮어쓰지 않는다.
4. 추적표의 화면·API·데이터·테스트 연결과 예시 응답을 같은 변경에서 갱신한다.
5. 해당 골든 테스트와 로컬 링크 검증 결과를 PR에 남긴다.
