# 요구사항 추적표

> 문서 상태: Review
> 최종 검토: 2026-07-12
> 범위: FinMate vNext MVP

## 사용 방법

이 표는 구현 순서가 아니라 누락 방지 계약이다. 후속 문서는 아래 ID를 그대로 사용한다. 화면·API·데이터·테스트 열이 모두 채워지기 전에는 해당 요구사항을 개발 착수 가능으로 보지 않는다.

- 화면 ID는 `02-ux/screen-specification.md`의 ID와 일치해야 한다.
- API는 `06-api/openapi.yaml` 서버 base URL을 포함한 `METHOD /api/v1/path (operationId: name)` 형식으로 기록한다. OpenAPI에 아직 선언되지 않은 공개 HTTP 계약은 경로나 operationId를 추측하지 않고 `미선언`으로 기록한다.
- 데이터 열은 `ERD:` marker 뒤에 `04-data/erd-and-data-dictionary.md`가 정의한 대문자 엔터티 이름을 하나 이상 정확히 기록하고, 이어서 `03-domain`의 상태·계산 규칙과 fixture 시나리오를 가리킨다.
- 테스트 ID는 `09-quality/qa-and-acceptance-plan.md`와 `golden-test-cases.yaml`에서 같은 이름으로 정의한다.
- `계약 상태`는 이 추적 행의 준비 상태다. 문서 자체의 상태 어휘는 상위 [vNext 안내서](../README.md)를 따른다.

## 계약 상태

| 상태 | 의미 | 개발 착수 |
| --- | --- | --- |
| `Scope locked` | 사용자 가치와 안전 경계는 결정됐지만 후속 명세가 아직 없다. | 불가 |
| `Mapped` | 화면·API·데이터·테스트의 대상 ID가 정해졌다. | mock만 가능 |
| `Ready` | 후속 명세와 예시가 모두 검토되어 구현자가 추가 결정을 할 필요가 없다. | 가능 |
| `Verified` | 구현과 자동 검증 결과가 연결됐다. | 릴리스 후보 가능 |

## 핵심 요구사항 매트릭스

| ID | 요구사항과 수용 기준 | 화면 | API | 데이터·계산 | 테스트 | 계약 상태 |
| --- | --- | --- | --- | --- | --- |
| RQ-001 | 사용자는 생활 맥락, 고민, 성향, 공개 설정, MyData 동의를 완료한 뒤 기준선 진단을 본다. 정량 목표는 기준선이 준비되기 전 제안하지 않는다. | `OB-01`, `OB-02`, `OB-03`, `H-01` | `PUT /api/v1/me/onboarding` (operationId: `saveOnboarding`); `POST /api/v1/mydata/connection` (operationId: `createMyDataConnection`); `GET /api/v1/mydata/connection` (operationId: `getMyDataConnection`); `GET /api/v1/home` (operationId: `getHome`) | ERD: `USER`, `MYDATA_CONNECTION`, `MYDATA_CONSENT`, `SHARE_CONSENT`, `FINANCIAL_BASELINE`; 기준선 최소 기간 규칙 | `ONBOARDING-COMPLETE-001` 정상 완료, `ONBOARDING-BASELINE-INSUFFICIENT-001` 기준선 부족 | Mapped |
| RQ-002 | 모든 계산 결과는 계산 버전, 데이터 상태, 마지막 동기화 시각을 가진다. `STALE`, `INSUFFICIENT`, `NEEDS_REVIEW`에서는 진행을 신뢰 가능한 값처럼 보이지 않는다. | `OB-03`, `H-01`, `G-01`, `R-01`, `C-01` | `GET /api/v1/home` (operationId: `getHome`); `POST /api/v1/mydata/syncs` (operationId: `requestMyDataSync`); `GET /api/v1/mydata/syncs/{syncId}` (operationId: `getMyDataSync`) | ERD: `SYNC_STATE`, `SYNC_RUN`, `CANONICAL_TRANSACTION`, `CLASSIFICATION_DECISION`, `FINANCIAL_BASELINE`, `ANIMAL_STAT_SNAPSHOT`, `GOAL_PROGRESS_SNAPSHOT`; 거래 정규화·중복 제거·분류 검토와 freshness 규칙 | `DATA-STALE-001` 오래됨, `MYDATA-SYNC-PARTIAL-001` 부분 반영, `CALC-TRANSFER-001` 내부이체·환불 | Mapped |
| RQ-003 | 비교 탐색은 공개 동의·최소 표본을 통과한 익명 루틴만 보여준다. 정확 코호트가 30명 미만이면 비금융 맥락만 넓히고, 안전 코호트가 30명 이상일 때만 `GROUP_ROUTINE`; 없으면 카드 없는 `INSUFFICIENT`다. | `M-01`, `M-02` | `GET /api/v1/adventurers` (operationId: `listAdventurers`); `GET /api/v1/adventurers/{cardId}` (operationId: `getAdventurer`) | ERD: `RECOMMENDATION_CARD`, `VERIFIED_ROUTINE`, `SHARE_CONSENT`, `VALIDATION_GROUP`; 30명 하한, 비금융 일반화, 구간화·억제 규칙 | `CARD-PUBLIC-FIELDS-001` 익명 응답, `CARD-GROUP-001` 표본 부족, `CARD-GROUP-BOUNDARY-001` 경계, `CONSENT-WITHDRAW-001` 철회 | Mapped |
| RQ-004 | 사용자는 추천 모험가의 루틴 하나를 선택하고, 코드가 계산한 `LIGHT`, `STANDARD`, `CHALLENGE` 후보를 비교한다. 데이터 부족이면 행동형 시작 목표만 제공한다. | `M-02`, `G-01` | `POST /api/v1/goal-candidate-sets` (operationId: `createGoalCandidateSet`); `GET /api/v1/goal-candidate-sets/{candidateSetId}` (operationId: `getGoalCandidateSet`) | ERD: `GOAL_CANDIDATE_SET`, `GOAL_CANDIDATE`, `FINANCIAL_BASELINE`, `VERIFIED_ROUTINE`, `VALIDATION_GROUP`; 템플릿 안전 범위, P25~P75 제한, 난이도 계수 | `GOAL-INCREASE-001` 저축률 후보, `CALC-DISPOSABLE-001` 여윳돈 0 이하, `GOAL-NO-IMPROVEMENT-001`·`GOAL-ROUNDING-001` 무의미한 후보 제거 | Mapped |
| RQ-005 | 후보는 사용자가 동의 버전과 함께 확정해야만 활성화된다. 사용자당 활성 목표는 하나이며 확정·일시정지·취소 요청은 멱등 처리한다. | `G-01`, `G-02`, `G-03`, `H-01` | `POST /api/v1/goals` (operationId: `createGoal`); `GET /api/v1/goals/active` (operationId: `getActiveGoal`); `POST /api/v1/goals/{goalId}/pause` (operationId: `pauseGoal`); `POST /api/v1/goals/{goalId}/resume` (operationId: `resumeGoal`); `POST /api/v1/goals/{goalId}/cancel` (operationId: `cancelGoal`) | ERD: `USER_GOAL`, `GOAL_CANDIDATE_SET`, `GOAL_CANDIDATE`, `FINANCIAL_BASELINE`, `MYDATA_CONSENT`; 후보 확정 뒤 ACTIVE, idempotency와 단일 열린 목표 규칙 | `GOAL-IDEMPOTENCY-001` 중복 확정, `GOAL-ACTIVE-CONFLICT-001` 활성 목표 충돌, `GOAL-CANDIDATE-EXPIRED-001` 후보 만료 | Mapped |
| RQ-006 | 활성 목표는 실제 데이터로만 진행률을 계산한다. 데이터가 오래되면 진행을 멈추고, 소득 또는 필수지출이 20% 이상 변하면 자동 수정 대신 재설정을 요청한다. | `H-01`, `G-03` | `GET /api/v1/goals/active` (operationId: `getActiveGoal`); `GET /api/v1/goals/{goalId}/history` (operationId: `listGoalHistory`); `POST /api/v1/goals/{goalId}/resume` (operationId: `resumeGoal`) | ERD: `USER_GOAL`, `GOAL_PROGRESS_SNAPSHOT`, `FINANCIAL_BASELINE`, `SYNC_STATE`; 재설정 조건과 목표 유형별 진행률 공식 | `DATA-STALE-001` 데이터 오래됨, `BASELINE-CHANGE-001` 20% 변화, `GOAL-PAUSE-001` 30일 초과 재개 | Mapped |
| RQ-007 | 홈은 목표 진행률을 3단계 자동 레이드로 보여준다. 새 데이터 뒤 짧은 연출 후 대기하며, 시간 누적 피해·재도전 횟수·실패 벌점·보스 회복은 없다. | `H-01` | `GET /api/v1/home` (operationId: `getHome`); `GET /api/v1/goals/active` (operationId: `getActiveGoal`) | ERD: `USER_GOAL`, `GOAL_PROGRESS_SNAPSHOT`, `RAID_PROGRESS`; 33/66/100 단계, 현재·최고 진행률, WAITING_FOR_DATA | `RAID-STAGE-BOUNDARY-001` 단계 계산, `RAID-HIGH-WATERMARK-001` 진행 하락, `RAID-WAITING-DATA-001` 데이터 대기 | Mapped |
| RQ-008 | 퀘스트는 목표를 돕는 행동이며 퀘스트 XP와 금융 스탯·목표 진행률은 분리한다. 투자 판단 퀘스트는 학습·점검만 제공한다. | `H-01`, `Q-01`, `Q-02` | `GET /api/v1/quests` (operationId: `listQuests`); `POST /api/v1/quests/{questId}/start` (operationId: `startQuest`); `POST /api/v1/quests/{questId}/complete` (operationId: `completeQuest`) | ERD: `USER_QUEST`, `QUEST_EVIDENCE`, `ANIMAL_STAT_SNAPSHOT`, `USER_GOAL`, `GOAL_PROGRESS_SNAPSHOT`; 검증 출처와 분리 반영 규칙 | `QUEST-EVIDENCE-PENDING-001` 검증 전 완료 불가, `QUEST-NO-DOUBLE-COUNT-001` XP와 목표 분리, `INVESTMENT-SAFETY-001` 투자 보상 차단 | Mapped |
| RQ-009 | 곰·물개·토끼·새는 한 사용자의 소비·저축·투자 판단·퀘스트/지식 상태를 보여준다. 낮은 상태를 비난하거나 투자금·수익률로 성장시키지 않는다. | `H-01`, `R-01` | `GET /api/v1/reports/animals/{animal}` (operationId: `getAnimalReport`); `GET /api/v1/home` (operationId: `getHome`) | ERD: `ANIMAL_STAT_SNAPSHOT`, `FINANCIAL_BASELINE`, `USER_GOAL`; 동물별 계산 입력과 표시 금지 규칙 | `REPORT-SPENDING-RANGE-001` 소비 범위 유지, `REPORT-INVESTMENT-NONMONETARY-001` 투자 판단 비금전성, `REPORT-NEUTRAL-COPY-001` 설명 문구 | Mapped |
| RQ-010 | 사용자는 30일 금융 여정, 일일 기록, 목표·단계 이력, 예산 안정도와 회고를 확인한다. 완료 후 값이 변해도 과거 달성 기록은 보존한다. | `J-01`, `J-02` | `GET /api/v1/records` (operationId: `listJourneyRecords`); `GET /api/v1/records/{date}` (operationId: `getDailyRecord`); `PUT /api/v1/records/{date}/reflection` (operationId: `saveDailyReflection`); `GET /api/v1/goals/{goalId}/history` (operationId: `listGoalHistory`) | ERD: `JOURNEY_RECORD`, `USER_GOAL`, `GOAL_PROGRESS_SNAPSHOT`, `RAID_PROGRESS`; 날짜 기준 스냅샷과 과거 달성 보존 | `RECORD-JOURNEY-30D-001` 30일 여정, `RECORD-HISTORY-PRESERVED-001` 완료 후 하락, `RECORD-DAILY-DETAIL-001` 일일 상세 | Mapped |
| RQ-011 | 코드는 후보 ID·수치를 만들고 AI는 서버 허용 후보만 순위·설명한다. API는 ID·근거·수치를 검증하며 AI 실패에는 결정론적 순위를 제공한다. | `G-01` | `POST /api/v1/goal-candidate-sets` (operationId: `createGoalCandidateSet`)와 `GET /api/v1/goal-candidate-sets/{candidateSetId}` (operationId: `getGoalCandidateSet`) 응답은 required `recommendation.recommendedCandidateId`, `reasonCodes`, `summary`, `riskNotice`, `recommendationState` (`AI_GENERATED` 또는 `DETERMINISTIC_FALLBACK`)를 제공해야 한다. 별도 공개 AI endpoint는 **미선언**이다. | ERD: `GOAL_CANDIDATE_SET`, `GOAL_CANDIDATE`, `AI_AUDIT_LOG`; 허용 후보 검증, 전체 출력 폐기, 결정론적 fallback과 AI 보존 규칙 | `AI-VALID-001`, `AI-REASON-MISMATCH-001`, `AI-UNKNOWN-ID-001`, `AI-NUMBER-MUTATION-001`, `AI-FALLBACK-NO-STANDARD-001`, `AI-AUDIT-RETENTION-001` | Mapped |
| RQ-012 | 사용자는 공개 동의를 versioned·idempotent·withdrawal-first 명령으로 철회한다. stale opt-in은 `412`이며 AI 철회·계정 삭제의 비법정 연결 기록은 7일 안에 제거되고 backup은 30일 안에 만료한다. | `OB-02`, `S-01` | `PUT /api/v1/me/privacy` (operationId: `savePrivacySettings`)는 body `consentAggregateId`, `expectedVersion`, `anonymousCardOptIn`, `exposedFields`, `consentVersion`, header `If-Match`, `Idempotency-Key`와 stale opt-in `412`를 선언한다. 별도 공개 동의 endpoint는 **미선언**이다. | ERD: `SHARE_CONSENT`, `RECOMMENDATION_CARD`, `VERIFIED_ROUTINE`, `AI_AUDIT_LOG`; WITHDRAWN terminal, withdrawal-first 직렬화, 삭제·보존·tombstone 정책 | `CONSENT-NOT-GRANTED-001`, `CONSENT-WITHDRAW-001`, `CONSENT-RACE-001`, `AI-CONSENT-WITHDRAW-DELETE-001`, `ACCOUNT-DELETE-001`, `BACKUP-RESTORE-001` | Mapped |
| RQ-013 | MVP는 친구·팔로잉·순위·파티 기여도·포인트·쿠폰·현금성 보상·상품/종목·투자금/수익률 목표·자동 푸시 캠페인을 제공하지 않는다. | `OB-01`, `OB-02`, `OB-03`, `H-01`, `M-01`, `M-02`, `G-01`, `G-02`, `G-03`, `Q-01`, `Q-02`, `R-01`, `J-01`, `J-02`, `C-01`, `S-01` | 음성 계약: 현재 OpenAPI에 소셜·보상·투자·자동 푸시 HTTP path와 operationId가 **미선언**이다. 선언된 각 `METHOD /api/v1/path`는 `docs/vnext/06-api/openapi.yaml`의 operationId 목록으로 검사한다. | ERD: `USER_GOAL`, `USER_QUEST`, `ANIMAL_STAT_SNAPSHOT`, `JOURNEY_RECORD`; 목표 템플릿·퀘스트·보상·이벤트 금지 목록 | `MVP-SAFETY-SURFACE-001` 금지 경로·필드·이벤트, `INVESTMENT-SAFETY-001` 투자 유도 0건, `DATA-STALE-001` 데이터 지연 벌점 0건 | Mapped |
| RQ-014 | 모바일 PWA는 mock API만으로 핵심 흐름을 시연할 수 있고, 서버는 합성 MyData 어댑터로 같은 흐름을 재현한다. Git 산출물이 정본이며 관찰 가능성과 오류 추적을 제공한다. | `H-01`, `M-01`, `M-02`, `G-01`, `G-02`, `Q-01`, `Q-02`, `R-01`, `J-01` | OpenAPI 전체의 각 `METHOD /api/v1/path (operationId: name)`가 대상이다. 핵심 시연은 `GET /api/v1/home` (operationId: `getHome`), `POST /api/v1/goal-candidate-sets` (operationId: `createGoalCandidateSet`), `POST /api/v1/goals` (operationId: `createGoal`), `POST /api/v1/mydata/syncs` (operationId: `requestMyDataSync`)를 사용한다. | ERD: `SYNC_RUN`, `SYNC_SCOPE_RESULT`, `FINANCIAL_BASELINE`, `GOAL_CANDIDATE_SET`, `USER_GOAL`, `AI_AUDIT_LOG`; adapter, fixture, calculation/audit/event version과 observability | `E2E-MOCK-CORE-001` mock 핵심 흐름, `E2E-SYNTHETIC-ADAPTER-001` synthetic adapter 흐름, `CONTRACT-OPENAPI-EXAMPLES-001` OpenAPI 예시 | Mapped |

## 추적 검증 규칙

1. 새 요구사항은 위 표에 추가한 뒤에만 화면·API·데이터 문서에 들어간다.
2. 화면이나 API를 삭제할 때는 연결된 요구사항의 수용 기준과 골든 테스트를 같은 변경에서 갱신한다.
3. `Ready` 전환에는 각 참조 문서의 `Approved` 상태, OpenAPI 예시, fixture, 자동 테스트 ID가 필요하다.
4. `Verified` 전환에는 단위·계산·계약·통합 또는 E2E 결과를 모두 기록한다. 수동 시연만으로는 충분하지 않다.
5. `RQ-013`은 음성 요구사항이다. 새 기능을 추가할 때마다 금지 목록에 없는지 자동 검사한다.
