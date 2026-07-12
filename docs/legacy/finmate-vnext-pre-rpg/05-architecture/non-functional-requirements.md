# FinMate vNext 비기능 요구사항

> 상태: Review
> 측정 단위: production 기준, 별도 표기가 없으면 월간 rolling window

## 1. 서비스 수준 목표

| ID | 요구사항 | 목표 | 측정/검증 |
| --- | --- | --- | --- |
| NFR-AVL-01 | Public API 가용성 | 월 99.9% 이상. 계획 점검 제외 | load balancer의 유효 요청 중 5xx·timeout 비율 |
| NFR-AVL-02 | 핵심 쓰기 가용성 | 목표 확정·상태 변경·퀘스트 완료 월 99.9% 이상 | operationId별 성공률 |
| NFR-PERF-01 | 단순 조회 지연 | `GET /me`, `/home`, `/goals/active` p95 300ms, p99 800ms 이하 | 서버 처리 시간, warm cache, AI 제외 |
| NFR-PERF-02 | 목록/리포트 지연 | 추천·퀘스트·기록·동물 리포트 p95 500ms, p99 1.2s 이하 | 최대 기본 page 크기에서 측정 |
| NFR-PERF-03 | 명령 지연 | 목표 확정·상태 변경·퀘스트 명령 p95 600ms 이하 | DB commit과 outbox 저장까지 |
| NFR-PERF-04 | AI 순위·설명 예산 | 2.5s timeout, timeout 시 100ms 이내 결정론적 fallback 순위 선택 | AI client 메트릭 |
| NFR-SCALE-01 | MVP 부하 | 동시 로그인 사용자 1,000명, public API 100 RPS에서 NFR-PERF 충족 | staging 15분 부하 시험 |

가용성 계산에서 인증 실패, validation error, rate limit, 명시적 stale-data 충돌은 서버 장애로 세지 않되 코드별 비율을 별도 관측한다.

## 2. 데이터 정확성과 일관성

| ID | 요구사항 | 수용 기준 |
| --- | --- | --- |
| NFR-DATA-01 | 계산 재현성 | 동일한 canonical event 집합·템플릿 버전·계산 버전은 bit-identical 목표값과 진행률을 생성한다. |
| NFR-DATA-02 | 금액 정확성 | KRW와 basis point 입력·저장은 int64 정수, 중간 산술은 `arbitrary-precision`이다. binary float, unchecked cast, 포화, wraparound를 사용하지 않고 저장 직전 `checked int64` 실패 시 전체 계산을 거절한다. |
| NFR-DATA-03 | 열린 목표 일관성 | DB 제약과 트랜잭션으로 사용자당 `CONFIRMED`/`ACTIVE`/`PAUSED` 목표를 합쳐 최대 하나다. |
| NFR-DATA-04 | 감사 이력 | 진행률 변경마다 입력 버전·계산 버전·현재/최고 진행률·변경 이유를 불변 스냅샷으로 남긴다. |
| NFR-DATA-05 | 데이터 계보 | 모든 금융 스냅샷을 provider batch와 canonical event 버전까지 역추적할 수 있다. |
| NFR-DATA-06 | freshness 명시 | 추천·후보·목표·퀘스트·연결·sync·거래·리포트·기록 등 모든 데이터 파생 성공 응답은 `dataFreshness`와 일치하는 `X-Data-State`를 포함한다. |
| NFR-DATA-07 | 중복 방지 | 같은 provider transaction key의 재수신은 provider 수정·수신 시각, response ID, record ordinal, hash 순서로 같은 winner를 선택하고 수입·지출·저축에 중복 반영되지 않는다. |
| NFR-DATA-08 | 목표 교체 원자성 | 재설정 확인에서 이전 목표 `CANCELLED/RECALIBRATED`, 새 목표 `ACTIVE`, 연결 퀘스트 종결·생성이 모두 commit되거나 모두 rollback된다. |
| NFR-DATA-09 | 퀘스트 일시정지 | 목표 연결 퀘스트는 목표 `PAUSED` 동안 완료·XP 지급 0건이며 유효 재개 또는 목표 종결 규칙으로만 빠져나온다. |

## 3. 신선도와 비동기 처리

| ID | 요구사항 | 목표 |
| --- | --- | --- |
| NFR-FRESH-01 | 수신 후 반영 | provider batch 수신 완료 후 canonical ledger와 read model 반영 p95 5분, p99 15분 이내 |
| NFR-FRESH-02 | 동기화 상태 | sync job 상태 변경은 30초 이내 조회 API에 표시 |
| NFR-FRESH-03 | outbox 지연 | publish lag p95 30초, p99 2분 이하 |
| NFR-FRESH-04 | 오래된 데이터 보호 | 템플릿 허용 기간 초과 시 진행률 갱신을 중지하고 `STALE`을 반환 |
| NFR-FRESH-05 | 공개 철회 | 조회 차단 즉시, 추천 캐시·파생 투영 제거 24시간 이내 |
| NFR-FRESH-06 | 부분 sync 정직성 | `PARTIAL_FAILED`는 `lastPartialSyncedAt`만 전진하고 공개 `lastSyncedAt`은 마지막 완전 `SUCCEEDED` 값 유지 |

## 4. 보안

| ID | 요구사항 | 수용 기준 |
| --- | --- | --- |
| NFR-SEC-01 | 전송·저장 암호화 | 외부 TLS 1.2 이상, DB·객체 저장소·backup은 관리형 키 암호화 |
| NFR-SEC-02 | 토큰 | access token 15분, refresh token 최대 30일, 매 refresh마다 회전·재사용 탐지. refresh는 `RefreshCookie`, logout은 bearer+같은 cookie 필수 |
| NFR-SEC-03 | 비밀번호 | Argon2id 또는 현재 OWASP 권고 수준의 adaptive hash, 평문·복호화 가능 저장 금지 |
| NFR-SEC-04 | 권한 | 모든 private endpoint에서 subject 기반 소유권 검사. 식별자 추측만으로 타인 자원 조회 불가 |
| NFR-SEC-05 | 속도 제한 | 로그인 IP+계정 5회/분, refresh 세션 10회/분, 일반 사용자 120회/분; `429`와 `Retry-After` 제공 |
| NFR-SEC-06 | 비밀 | 코드·fixture·로그에 운영 secret 금지. secret manager와 90일 이하 회전 정책 사용 |
| NFR-SEC-07 | 취약점 | release 시 Critical/High 알려진 취약점 0건. Medium은 소유자와 기한 기록 |
| NFR-SEC-08 | 감사 | 동의, 공개 철회, 운영자 조회, 목표 확정, AI 노출 결과를 사용자·시각·이유와 기록 |

OWASP ASVS Level 2의 인증, 세션, 접근통제, 입력 검증 항목을 release gate로 사용한다. 운영자 기능은 public API와 별도 권한·네트워크 경계를 사용한다.

## 5. 개인정보와 AI 안전

| ID | 요구사항 | 수용 기준 |
| --- | --- | --- |
| NFR-PRV-01 | 공개 allowlist | 추천 응답 schema에 원본 사용자 ID, 정확 금액, 거래 원문, 상품·종목, 수익률 필드가 존재하지 않는다. |
| NFR-PRV-02 | 최소 표본 | 개인 카드는 30명 이상 검증 그룹, 그룹 카드는 `cohortSize >= 30`을 통과한다. 30명 미만 집계 생성 0건 |
| NFR-PRV-03 | 로그 최소화 | 계좌·카드 번호, 토큰, 쿠키, 거래 설명, 타인 정확 금액을 로그·trace attribute에 기록하지 않는다. |
| NFR-PRV-04 | 삭제/철회 | 철회 이후 새 추천 0건. AI 동의 철회·계정 삭제 후 모든 non-`LEGAL_REQUIRED` 연결 레코드·콘텐츠는 7일 이내 삭제 또는 비가역 분리한다. |
| NFR-PRV-05 | 안전한 fallback | 정확 코호트가 30명 미만이면 비금융 생활맥락만 일반화하고 금융 수용력 제약은 유지한다. 안전한 상위 코호트가 30명 이상일 때만 `GROUP_ROUTINE`; 없으면 빈 `INSUFFICIENT` 응답 |
| NFR-PRV-06 | AI 기록 보존 | active-consent `OPERATIONAL` 최대 90일. 유효한 `INCIDENT_EVIDENCE` 최대 180일이며 철회·삭제 전 완전한 `LEGAL_REQUIRED` 재분류 없이는 그 event를 넘겨 존속 0건 |
| NFR-PRV-07 | 삭제 복원 | backup 최대 30일. 모든 복원은 서비스 개방 전 철회·삭제 tombstone을 재생하고 대상 레코드·재연결 매핑 재등장 0건 |
| NFR-AI-01 | 후보·수치 권한 | 코드는 후보 ID·금액·비율·기간을 만들고, AI는 서버 allowlist 후보만 순위·설명한다. ID·수치·근거 불일치 출력은 전체 폐기하고 노출하지 않는다. |
| NFR-AI-02 | fallback | AI가 없거나 2.5s를 넘기거나 검증에 실패해도 결정론적 순위로 후보 생성·목표 확정·기록 조회 성공률은 변하지 않는다. |
| NFR-AI-03 | 투자 가드레일 | 투자금 증액·거래·수익률을 목표, XP, 보상, 축하 문구로 생성한 건수 0건 |
| NFR-AI-04 | 순위 응답 | 모든 후보 응답에 required `recommendation.recommendedCandidateId`, `reasonCodes`, `summary`, `riskNotice`, `recommendationState` (`AI_GENERATED` 또는 `DETERMINISTIC_FALLBACK`); 추천 ID는 응답 후보 allowlist에 포함되고 후보 수치 변형 0건 |

## 6. 복구와 연속성

| ID | 요구사항 | 목표/검증 |
| --- | --- | --- |
| NFR-DR-01 | 복구 목표 | PostgreSQL RPO 15분, RTO 2시간 |
| NFR-DR-02 | backup | 일 1회 full + 연속 WAL, 최대 30일 보관, 월 1회 격리 환경에서 tombstone 재생 포함 복원 시험 |
| NFR-DR-03 | 원본 재처리 | 원본 객체와 adapter version으로 canonical ledger를 결정론적으로 재구축 가능 |
| NFR-DR-04 | outbox 재처리 | consumer는 멱등하며 checkpoint 이후 안전하게 재생 가능 |
| NFR-DR-05 | AI 장애 | Intelligence Service 전체 중단 시 public API 핵심 흐름은 fallback으로 지속 |

## 7. 관측성과 운영

| ID | 요구사항 | 수용 기준 |
| --- | --- | --- |
| NFR-OBS-01 | 상관관계 | 모든 응답에 `X-Request-Id`; API→worker→AI에 trace context 전파 |
| NFR-OBS-02 | 구조화 로그 | timestamp, level, service, environment, traceId, operationId, resultCode, latencyMs를 JSON으로 기록 |
| NFR-OBS-03 | SLI | operationId별 요청 수·오류·지연, DB pool, outbox lag, sync lag, AI timeout/검증 실패를 계측 |
| NFR-OBS-04 | 경보 | 5분 5xx 2% 초과, p95 2배 초과, outbox p99 5분 초과, sync failure 10% 초과 시 호출 |
| NFR-OBS-05 | 감사 분리 | 보안 감사 로그는 앱 로그와 분리하고 변경 방지 저장소에 기록 |
| NFR-OBS-06 | 응답 추적 | OpenAPI의 성공·오류·204·304 모든 응답에 `X-Request-Id`; 적용 가능한 모든 operation에 표준 429/500 선언 |

로그 보관은 앱 로그 30일, trace 14일, 메트릭 13개월을 기본값으로 한다. AI 감사 로그는 `OPERATIONAL` 최대 90일, `INCIDENT_EVIDENCE` 최대 180일과 7일 철회·삭제 규칙을 우선 적용하며, 완전한 `LEGAL_REQUIRED`만 근거별 만료일까지 격리한다.

## 8. 접근성과 호환성

| ID | 요구사항 | 수용 기준 |
| --- | --- | --- |
| NFR-UX-01 | 접근성 | 핵심 MVP 화면 WCAG 2.2 AA, 키보드 탐색과 스크린리더 E2E 통과 |
| NFR-UX-02 | 모션 | `prefers-reduced-motion`과 앱 설정에서 레이드 연출 축소/비활성화 가능 |
| NFR-COMP-01 | 브라우저 | 출시일 기준 최신 2개 Chrome/Safari/Edge, iOS Safari 16.4 이상 |
| NFR-COMP-02 | API 호환 | 같은 major에서 기존 required field 제거·의미 변경 금지, additive 변경만 허용 |
| NFR-COMP-03 | fixture | OpenAPI에 연결된 모든 JSON fixture가 CI에서 해당 schema validation 통과 |
| NFR-COMP-04 | MVP adapter | 실제 provider 자격증명 없이 `SYNTHETIC` adapter로 성공·부분 실패·STALE·INSUFFICIENT 흐름 재현 |

## 9. 품질 게이트

배포 후보는 다음을 모두 통과해야 한다.

1. OpenAPI 3.0.3 lint와 모든 `$ref` 해석 성공.
2. 모든 JSON fixture의 연결 schema 검증 성공.
3. 모든 operation의 `X-Request-Id`, 429/500과 모든 데이터 파생 operation의 body/header freshness audit 성공.
4. `INDIVIDUAL | GROUP_ROUTINE`, `MetricValue`, `TargetDefinition` discriminator와 그룹 `cohortSize >= 30` schema 검증 성공.
5. 목표·레이드·퀘스트·sync 상태 전이와 금융 계산 golden test 100% 통과.
6. 핵심 flow contract/integration/E2E 100% 통과.
7. NFR-SCALE-01 부하에서 오류율 1% 미만이며 성능 SLO 충족.
8. Critical/High 보안 취약점과 개인정보 allowlist 위반 0건.
9. backup 복원 시험 또는 최근 30일 안의 성공 증거 존재.
10. 후보 `recommendation`의 `AI_GENERATED`·`DETERMINISTIC_FALLBACK` 상태, 동의 stale opt-in `412`, AI 7일 삭제와 backup tombstone 재생 계약 검증 성공.
