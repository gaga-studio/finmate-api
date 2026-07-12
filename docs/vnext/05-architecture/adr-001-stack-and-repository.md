# ADR-001: 기술 스택과 저장소 경계

> 상태: Approved
> 결정일: 2026-07-12
> 결정 소유자: FinMate vNext 팀

## 1. 배경

FinMate는 이미 별도 React/Vite 프론트엔드 저장소와 Spring Boot 백엔드 저장소를 운영하며, 백엔드 저장소 안에 Python Intelligence Service와 OpenAPI, 합성 데이터 도구가 있다. vNext MVP는 3~4주 안에 명시적 `/api/v1` 계약으로 핵심 흐름을 재구축해야 한다. 목표·레이드·퀘스트·마이데이터의 상태 일관성이 중요하지만, 현재 팀 규모에서 서비스별 운영 복잡도를 감당할 이유는 아직 없다.

## 2. 결정

다음을 기본 스택과 저장소 경계로 채택한다.

| 영역 | 선택 |
| --- | --- |
| Frontend | 별도 `finmate-frontend` 저장소, React + TypeScript + Vite PWA, OpenAPI 생성 타입 |
| Public API | 이 저장소의 Spring Boot 3.5, Java 17, Gradle |
| Backend 구조 | 도메인 경계를 가진 모듈러 모놀리스, 동기 REST + transactional outbox worker |
| Persistence | PostgreSQL 16, Flyway, JPA는 aggregate 쓰기용, SQL projection은 조회용 |
| MyData | provider port와 canonical source를 둔 adapter 모듈; MVP의 유일 구현은 버전 고정 `SYNTHETIC` provider |
| AI | 이 저장소의 Python 3.12/FastAPI Intelligence Service를 내부 서비스로 유지 |
| Contract | `docs/vnext/06-api/openapi.yaml`을 vNext 공개 API의 단일 기준으로 사용 |
| Observability | OpenTelemetry 규약, 구조화 JSON 로그, Prometheus 호환 메트릭 |

실제 사업자 adapter와 운영 자격증명은 post-MVP 별도 결정이다. MVP에서도 합성 adapter는 pagination, 중복 winner, partial failure, `lastSyncedAt`/`lastPartialSyncedAt`, 오류 코드를 포함한 전체 port 계약을 실행한다.

수치 구현은 JSON integer를 lossless하게 읽고 Java `BigInteger` 같은 `arbitrary-precision` 타입으로 중간 산술을 수행한 뒤 `Math.*Exact` 또는 동등한 `checked int64` 경계에서만 `long`으로 저장한다. KRW나 basis point에 `double`/`float`를 사용하는 구현은 스택 선택과 무관하게 금지한다.

Java 21 전환은 vNext 기능 구현과 분리한다. MVP 기준선은 현재 빌드와 운영 지식을 재사용할 수 있는 Java 17이며, 지원 종료 또는 측정된 런타임 이점이 생길 때 별도 ADR로 올린다.

## 3. 검토한 대안

### 3.1 저장소 구성

| 선택지 | 장점 | 비용/위험 | 판단 |
| --- | --- | --- | --- |
| 현재형 polyrepo: 프론트 분리, 백엔드·계약·데이터·AI 결합 | 팀 소유권 명확, 기존 CI/배포 재사용, API 계약으로 협업 가능 | 원자적 프론트/백 변경이 어려움 | **채택** |
| 전체 monorepo | 한 PR에서 UI·API·fixture 변경, 공통 도구 관리 용이 | 저장소 이전과 CI 재구성이 MVP 일정에 영향 | 보류 |
| 서비스별 polyrepo | 독립 배포·권한·확장 | 계약 동기화, 로컬 환경, 운영 부담 증가 | 기각 |

프론트와 백엔드를 동시에 바꿔야 하는 변경은 OpenAPI 변경을 먼저 병합하고, 생성 클라이언트와 외부 JSON fixture로 프론트 작업을 분리한다.

### 3.2 백엔드 런타임

| 선택지 | 장점 | 비용/위험 | 판단 |
| --- | --- | --- | --- |
| Spring Boot 모듈러 모놀리스 | 기존 인증·DB·테스트 자산 활용, 강한 트랜잭션, 목표 상태 일관성 | 모듈 경계를 코드 리뷰로 지켜야 함 | **채택** |
| TypeScript/Node BFF 재작성 | 프론트와 타입 생태계 통일, 빠른 JSON 개발 | 기존 백엔드 재작성, 금융 계산/트랜잭션 회귀 위험 | 기각 |
| 초기 마이크로서비스 | 독립 확장·배포 | 분산 트랜잭션, 관측·배포·로컬 개발 비용 | 기각 |

### 3.3 API 스타일

| 선택지 | 장점 | 비용/위험 | 판단 |
| --- | --- | --- | --- |
| REST/OpenAPI, 도메인별 명시 스키마 | 생성 타입, mock, 계약 테스트, 캐시/관측 표준화 | 화면별 여러 조회가 생길 수 있음 | **채택** |
| 기존 범용 screen JSON | 화면 조립이 빠름 | 타입 안정성과 변경 영향 분석이 약함 | 기각 |
| GraphQL | 화면별 선택 조회, 단일 endpoint | 권한·캐시·오류·생성 도구 운영 추가 | 보류 |

### 3.4 데이터 접근

| 선택지 | 장점 | 비용/위험 | 판단 |
| --- | --- | --- | --- |
| PostgreSQL + JPA write/SQL read | 트랜잭션과 유연한 조회를 모두 확보 | 두 접근 규칙을 문서화해야 함 | **채택** |
| JPA만 사용 | 단일 방식 | 리포트·기록 projection에서 N+1과 복잡한 entity 위험 | 기각 |
| event sourcing 전면 도입 | 완전한 이력과 재생 | 구현·운영 비용이 MVP 가치보다 큼 | 기각 |

진행률 감사에는 전면 event sourcing 대신 불변 `GoalProgressSnapshot`과 outbox를 사용한다.

### 3.5 AI 배치

| 선택지 | 장점 | 비용/위험 | 판단 |
| --- | --- | --- | --- |
| 내부 Python 서비스 + 결정론적 fallback | 기존 임베딩/검증 자산 재사용, API와 장애 격리 | 두 런타임 운영 | **채택** |
| Java 프로세스에 AI 통합 | 배포 단순화 | Python 모델 생태계와 기존 자산 포기 | 기각 |
| 외부 LLM을 프론트에서 직접 호출 | 구현이 단순해 보임 | 비밀·개인정보·출력 검증·감사 통제 불가 | 금지 |

내부 Python 서비스도 API가 보낸 서버 허용 후보 ID의 순위·설명만 반환한다. 후보 ID와 모든 수치는 도메인 코드가 만들고 API가 AI 출력을 검증하며, AI가 없거나 유효하지 않으면 API가 같은 `recommendation` 응답 필드로 결정론적 순위를 제공한다.

## 4. 결과

### 긍정적 결과

- 현재 팀이 익숙한 배포·보안·테스트 자산을 재사용한다.
- 목표 확정과 상태 전이를 한 DB 트랜잭션으로 보장한다.
- 프론트는 백엔드 완성 전에도 OpenAPI와 fixture로 개발할 수 있다.
- AI와 마이데이터 제공자 장애를 제품 핵심 계산에서 격리한다.
- 실제 금융정보 없이도 합성 adapter와 OpenAPI fixture로 성공·부분 실패·오래됨·부족 상태를 재현한다.

### 감수하는 비용

- 프론트와 백엔드 PR 순서를 계약 중심으로 조정해야 한다.
- 모듈러 모놀리스의 테이블·패키지 경계를 자동 검사해야 한다.
- Python 서비스 배포, 모델 캐시, timeout을 별도로 관측해야 한다.
- projection의 최종 일관성을 UI가 `dataFreshness`와 계산 시각으로 표현해야 한다.

## 5. 전환 신호

다음 조건이 2개 릴리스 이상 지속되면 별도 서비스 분리를 검토한다.

- 특정 모듈의 독립 확장 요구가 API 전체 대비 5배 이상이다.
- 모듈 소유 팀과 배포 주기가 명확히 분리되고 결합 배포가 주된 병목이다.
- outbox consumer 지연이 API 자원 경쟁 때문에 월 SLO를 반복 위반한다.
- 보안 또는 규제 경계가 별도 데이터 저장소와 운영 권한을 요구한다.

다음 조건이 충족되기 전에는 전체 monorepo 이전을 하지 않는다.

- 한 변경의 50% 이상이 프론트·백엔드 원자적 커밋을 반복적으로 요구한다.
- 통합 CI 시간이 현재 두 저장소 CI 합계보다 짧아진다는 실측 계획이 있다.
- 배포 권한과 비밀 분리 방식을 먼저 확정했다.

## 6. 준수 확인

- 새 공개 endpoint가 `/api/v1` OpenAPI에 먼저 정의되어 있는가.
- domain 코드가 Spring/JPA/LLM SDK에 의존하지 않는가.
- 프론트 응답이 persistence entity나 범용 `data` bag을 노출하지 않는가.
- AI 없이 동일한 후보·목표·진행률을 생성할 수 있는가.
- AI가 서버 허용 후보 ID만 순위·설명하고 public API가 `recommendation.recommendedCandidateId`, `reasonCodes`, `summary`, `riskNotice`, `recommendationState = AI_GENERATED | DETERMINISTIC_FALLBACK`을 검증·제공하는가.
- 실제 provider 자격증명 없이 `SYNTHETIC` adapter로 전체 contract test를 실행할 수 있는가.
- 정수 중간 계산과 int64 저장 경계에 floating point나 unchecked cast가 없는가.
- 서비스 분리 제안에 측정된 전환 신호가 포함되어 있는가.
