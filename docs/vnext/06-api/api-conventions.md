# FinMate vNext API 규칙

> 상태: Review
> 기준 명세: `openapi.yaml`
> Base path: `/api/v1`

## 1. 계약 원칙

- OpenAPI 3.0.3 파일이 공개 API의 단일 기준이다.
- JSON property는 `camelCase`, enum은 `UPPER_SNAKE_CASE`, operationId는 동사로 시작하는 `camelCase`를 사용한다.
- URL은 복수 명사와 kebab-case를 사용한다. 상태 전이는 의미가 분명한 action subresource를 사용한다.
- 시간은 UTC RFC 3339 date-time, 날짜는 `YYYY-MM-DD`, 월은 `YYYY-MM`이다.
- KRW는 소수 없는 integer, 비율과 진행률은 `0..10000` basis point 정수다. `1400`은 14%다.
- JSON integer는 lossless parser로 읽고 곱셈·가중합·분자는 `arbitrary-precision` 정수로 계산한다. 저장 직전 `checked int64` 변환에 실패하면 `500 NUMERIC_OVERFLOW`로 전체 처리를 중단하며 binary floating point, 포화, wraparound를 허용하지 않는다.
- API는 persistence entity나 임의의 `data` bag을 노출하지 않는다.
- 클라이언트는 명세에 없는 응답 property를 무시해야 한다.
- MVP의 마이데이터 연결 provider는 `SYNTHETIC`만 허용한다. 합성 어댑터도 실제 adapter port와 같은 pagination, 중복, 부분 실패, 신선도 계약을 실행한다.

## 2. 버전과 호환성

- major version은 URI의 `/api/v1`로 표현한다.
- 같은 major에서는 optional field·endpoint·enum 추가만 허용한다. required field 제거, 타입 변경, 기존 enum 의미 변경은 `/api/v2`가 필요하다.
- enum 추가 가능성에 대비해 생성 클라이언트는 unknown 값을 보존하거나 안전한 기본 화면을 사용한다.
- 폐기 endpoint는 최소 90일 전 `Deprecation: true`, `Sunset`, 문서 `Link` header를 제공한다.
- 요청의 `Accept` 기본값은 `application/json`; 오류는 `application/problem+json`이다.

## 3. 인증과 세션

### 3.1 Access token

- 보호 endpoint는 `Authorization: Bearer <access-token>`을 요구한다.
- access token은 서명된 JWT이며 수명은 15분이다.
- 최소 claim은 `sub`, `iss`, `aud`, `iat`, `exp`, `jti`다. 이메일·생활태그·금융값은 token에 넣지 않는다.
- 만료/위조 token은 `401 AUTH_TOKEN_INVALID`; 권한 부족은 `403 FORBIDDEN`이다.

### 3.2 Refresh session

- refresh token은 opaque random value이며 `HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth` cookie로만 전달한다.
- 최대 수명은 30일이고 refresh마다 회전한다. 이미 사용한 token 재사용을 탐지하면 token family 전체를 폐기한다.
- `POST /auth/refresh`는 body를 받지 않고 cookie를 사용한다.
- OpenAPI의 `RefreshCookie` security scheme은 이름이 `finmate_refresh`인 cookie다. `POST /auth/refresh`는 이 scheme이 필수다.
- `POST /auth/logout`은 bearer access token, `RefreshCookie`, `Idempotency-Key`를 모두 요구해 폐기할 현재 session을 명시한다.

브라우저는 access token을 장기 저장소에 보관하지 않는다. refresh cookie를 사용하므로 API는 명시된 frontend origin만 credentialed CORS로 허용한다.

## 4. 성공 응답과 상태 코드

| 코드 | 사용 |
| --- | --- |
| `200 OK` | 동기 조회, 기존 자원의 상태 변경 |
| `201 Created` | 가입, 목표 생성 같은 새 자원 생성. `Location` 제공 |
| `202 Accepted` | 비동기 MyData sync 접수 |
| `204 No Content` | logout처럼 body가 필요 없는 성공 |
| `304 Not Modified` | `If-None-Match` 기반 조건부 조회 |

읽기와 계산 성공 응답은 operation의 명시적 도메인 schema를 그대로 반환한다. 데이터 파생 응답은 그 도메인 payload의 최상위에 필수 `dataFreshness`를 함께 두며 공통 `{ "result": ... }` envelope를 사용하지 않는다. 목록은 `items`와 `page`를 사용한다. `04-data` 합성 시나리오의 `calculation-envelope.result`는 계산 재현용 내부 fixture 형식일 뿐 공개 HTTP 응답 형식이 아니다.

```json public-api-payload-shape
{
  "successBody": "DOMAIN_PAYLOAD",
  "dataDerivedMetadata": ["dataFreshness"],
  "forbiddenTopLevelFields": ["result"],
  "calculationEnvelopeScope": "INTERNAL_FIXTURES_ONLY"
}
```

멱등 replay는 상태 코드를 `200`으로 바꾸지 않는다. 가입 replay는 최초 `201`, sync 요청 replay는 최초 `202`, body 없는 logout replay는 최초 `204`를 header와 body까지 그대로 반환한다.

## 5. 표준 오류

모든 오류는 RFC 9457 형태의 `Problem`을 사용한다.

```json
{
  "type": "https://api.finmate.kr/problems/data-stale",
  "title": "최신 금융 데이터가 필요합니다",
  "status": 409,
  "detail": "마지막 동기화 이후 35일이 지나 정량 목표를 확정할 수 없습니다.",
  "instance": "/api/v1/goals",
  "code": "DATA_STALE",
  "traceId": "01J2QZ6Q6V9M9M8W4Y2K5H7S3A",
  "retryable": false,
  "occurredAt": "2026-07-12T03:20:18Z"
}
```

다음 registry는 `ProblemCode`, 재설정 conflict, `RecalibrationReason`의 executable 정본이다.

```json contract-codes
{
  "errorCodes": [
    "ACTIVE_GOAL_EXISTS",
    "AUTH_REQUIRED",
    "AUTH_TOKEN_INVALID",
    "BUSINESS_RULE_VIOLATION",
    "CANDIDATE_EXPIRED",
    "CONSENT_REQUIRED",
    "DATA_INSUFFICIENT",
    "DATA_STALE",
    "DEPENDENCY_UNAVAILABLE",
    "FORBIDDEN",
    "IDEMPOTENCY_CONFLICT",
    "INTERNAL_ERROR",
    "INVALID_CURSOR",
    "MALFORMED_REQUEST",
    "NUMERIC_OVERFLOW",
    "PRECONDITION_FAILED",
    "RATE_LIMITED",
    "RECALIBRATION_REQUIRED",
    "RESOURCE_NOT_FOUND",
    "VALIDATION_FAILED"
  ],
  "idempotencyConflictCode": "IDEMPOTENCY_CONFLICT",
  "recalibrationConflictCode": "RECALIBRATION_REQUIRED",
  "recalibrationReasons": [
    "BASELINE_CHANGED_2000_BPS_OR_MORE",
    "TEMPLATE_INACTIVE",
    "REQUIRED_CONSENT_CHANGED",
    "PAUSE_EXCEEDED_P30D"
  ]
}
```

| HTTP | 대표 code | 의미 |
| --- | --- | --- |
| 400 | `MALFORMED_REQUEST`, `VALIDATION_FAILED` | JSON 구문, 형식, field validation 실패 |
| 401 | `AUTH_REQUIRED`, `AUTH_TOKEN_INVALID` | 인증 없음/실패 |
| 403 | `FORBIDDEN`, `CONSENT_REQUIRED` | 권한 또는 유효 동의 없음 |
| 404 | `RESOURCE_NOT_FOUND` | 현재 사용자에게 보이지 않는 자원 포함 |
| 409 | `ACTIVE_GOAL_EXISTS`, `CANDIDATE_EXPIRED`, `DATA_STALE`, `IDEMPOTENCY_CONFLICT`, `RECALIBRATION_REQUIRED` | 현재 상태와 명령 충돌 |
| 412 | `PRECONDITION_FAILED` | `If-Match`가 최신 resource version과 불일치 |
| 422 | `BUSINESS_RULE_VIOLATION`, `DATA_INSUFFICIENT` | 문법은 맞지만 도메인 규칙상 처리 불가 |
| 429 | `RATE_LIMITED` | 호출 제한; `Retry-After` 제공 |
| 500 | `INTERNAL_ERROR`, `NUMERIC_OVERFLOW` | 예상하지 못한 서버 오류 또는 안전한 정수 범위 초과 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 필수 의존 서비스 일시 장애 |

field 오류는 `errors[]`에 JSON Pointer `pointer`, 안정적인 `reason`, 사용자 표시 가능한 `message`를 넣는다. production의 `detail`에 stack trace, SQL, provider payload를 노출하지 않는다.

모든 operation은 적용 가능한 표준 `429`와 `500` 응답을 선언한다. 성공, 오류, `204`, `304`를 포함한 **모든** 응답은 `X-Request-Id`를 반환하고 reusable error response도 같은 header component를 사용한다.

## 6. 커서 페이지네이션

목록은 opaque cursor를 사용한다.

```json
{
  "items": [],
  "page": {
    "limit": 20,
    "hasMore": true,
    "nextCursor": "eyJzb3J0S2V5IjoiMjAyNi0wNy0xMlQwMDowMDowMFoifQ"
  }
}
```

- 기본 `limit`은 20, 최대 50이다.
- cursor에는 정렬 키와 filter hash를 서명해 넣으며 클라이언트가 해석하지 않는다.
- 다음 요청은 같은 filter·sort와 `cursor`를 사용한다. 다른 filter에 cursor를 재사용하면 `400 INVALID_CURSOR`다.
- 마지막 페이지는 `hasMore: false`, `nextCursor: null`이다.
- 안정 정렬은 endpoint별 주 정렬 키 + resource ID를 사용한다.

## 7. 멱등성

OpenAPI에서 표시된 명령은 UUID 형식의 `Idempotency-Key` header가 필수다.

- 키의 범위는 인증 사용자 + HTTP method + canonical path다.
- 서버는 요청 body hash, 최종 status, response body, 주요 response header를 24시간 저장한다.
- 같은 키와 같은 body의 재요청은 최초 응답을 반환하고 `Idempotency-Replayed: true`를 설정한다.
- 같은 키에 다른 body를 사용하면 `409 IDEMPOTENCY_CONFLICT`다.
- 처리 중 연결이 끊기면 동일 키로 재시도한다. 새 키를 만들면 중복 명령으로 취급될 수 있다.
- DB unique constraint가 목표 확정·퀘스트 검증 같은 도메인 중복도 최종 차단한다.

가입, 공개 설정 변경, 퀘스트 취소를 포함해 OpenAPI에 표시된 명령은 `Idempotency-Key`를 사용한다. 로그인·refresh와 GET에는 사용하지 않는다.

## 8. 낙관적 동시성

- `Goal`, `Quest`, `PrivacySettings`, `DailyReflection`의 변경 응답은 강한 `ETag`를 제공한다.
- 목표 pause/resume/cancel, 교체 후보 생성·확인, 퀘스트 취소, 공개 설정 변경, 회고 수정은 `If-Match`가 필수다.
- 일치하지 않으면 `412 PRECONDITION_FAILED`이며 서버가 자동 병합하지 않는다.
- 클라이언트는 최신 자원을 다시 조회하고 사용자 의도를 재적용한다.

`PUT /me/privacy` body의 `consentAggregateId`와 정수 `expectedVersion`은 응답의 같은 aggregate ID와 증가된 정수 `version`에 대응한다. `If-Match`와 `expectedVersion` 중 하나라도 최신 version과 다르면 변경을 적용하지 않는다. 특히 이전 version에서 만든 opt-in이 지연된 사이 철회가 먼저 commit되면 지연 요청은 새 멱등성 키를 사용해도 `412 PRECONDITION_FAILED`이고, 최신 `WITHDRAWN` 상태와 version은 유지된다.

멱등성은 네트워크 재시도를, `If-Match`는 서로 다른 화면·기기의 동시 수정을 해결한다. 둘은 서로 대체하지 않는다.

## 9. 데이터 신선도

데이터에서 파생된 모든 성공 응답은 필수 `dataFreshness`를 포함하고 `X-Data-State` header에 body의 `dataFreshness.state`와 같은 값을 보낸다. 대상은 추천 목록·상세, 후보 세트, 목표·이력·모든 목표 전이, 퀘스트·모든 퀘스트 전이, MyData 연결·동기화, 기준선·거래, 리포트, 여정·일일 기록·회고 쓰기다.

| state | 의미 | API 동작 |
| --- | --- | --- |
| `FRESH` | 템플릿 허용 기간 안의 검증 데이터 | 정상 계산/확정 |
| `PENDING` | 사용 행동은 있으나 provider 반영 전 | 마지막 검증값 유지, 진행률·퀘스트를 대기 상태로 표시 |
| `STALE` | 허용 기간보다 오래됨 | 조회는 200, 진행률 갱신 정지, 정량 후보 확정은 409 |
| `INSUFFICIENT` | 최소 기준선 미충족 | 행동형 후보만 제공하거나 422 |
| `NEEDS_REVIEW` | 중복·분류 충돌·이상치 확인 필요 | 영향 지표 제외, `blockedOperations` 제공 |

`asOf`는 계산에 사용한 원천 기준 시각, `lastSyncedAt`은 필요한 범위가 **모두 `SUCCEEDED`**인 마지막 완전 sync 기준 시각, `calculatedAt`은 현재 투영 계산 시각이다. `PARTIAL_FAILED`가 성공 범위를 커밋하면 `lastPartialSyncedAt`만 전진할 수 있고 `lastSyncedAt`은 이전 완전 성공 값으로 유지한다. 모든 계산 응답은 `calculationVersion`과 `sourceDataVersion`도 포함한다.

null은 계약이 명시한 경우만 허용한다. `asOf`와 `lastSyncedAt`은 최초 완전 sync 전 또는 금융 원천을 쓰지 않는 앱 이벤트 전용 결과, `lastPartialSyncedAt`은 부분 커밋 이력이 없을 때, 각 상태·종결 시각은 그 상태에 아직 진입하지 않았을 때만 null이다.

오래된 응답도 cache나 네트워크 실패로 우연히 생긴 stale이 아니라 제품 상태다. 따라서 오류로 숨기지 않고 200과 마지막 검증값을 반환한다. 다만 그 값을 근거로 상태를 바꾸는 명령은 `DATA_STALE`로 거부한다.

`SyncJob`은 생성된 한 번의 실행만 표현하므로 상태는 `REQUESTED | FETCHING | NORMALIZING | RECONCILING | SUCCEEDED | PARTIAL_FAILED | FAILED | BLOCKED`다. `IDLE`은 실행 상태가 아니며 연결 가능성은 `MyDataConnection.status`, scheduler readiness는 별도 운영 상태로 관리한다. `SUCCEEDED | PARTIAL_FAILED | FAILED | BLOCKED` 종결 실행은 삭제하거나 `IDLE`로 되돌리지 않고 `GET /mydata/syncs/{syncId}`에서 계속 조회할 수 있다.

핵심 read의 성공 example은 다음 endpoint별 matrix를 충족해야 한다. `getAdventurer`는 목록이 `INSUFFICIENT`이면 상세로 읽을 card가 없으므로 `STALE`만 적용한다.

```json state-coverage-matrix
{
  "getFinancialBaseline": ["INSUFFICIENT", "STALE"],
  "getHome": ["INSUFFICIENT", "STALE"],
  "getActiveGoal": ["INSUFFICIENT", "STALE"],
  "getGoal": ["INSUFFICIENT", "STALE"],
  "getAnimalReport": ["INSUFFICIENT", "STALE"],
  "listAdventurers": ["INSUFFICIENT", "STALE"],
  "getAdventurer": ["STALE"]
}
```

## 10. 캐시와 조건부 조회

- 개인 응답은 `Cache-Control: private, no-cache`; token/cookie 응답은 `no-store`다.
- 추천 카드 목록은 사용자별 필터 결과이므로 공유 CDN에 저장하지 않는다.
- `GET /home`, `/goals/{id}`, `/records/{date}`는 ETag를 지원한다.
- 세 조회는 optional `If-None-Match`를 받으며 일치하면 body 없는 `304`와 현재 `ETag`, `X-Request-Id`, 캐시된 표현과 같은 `X-Data-State`를 반환한다. header가 없으면 정상 `200` 표현을 반환한다.
- 공개 철회와 동의 변경은 관련 application cache를 즉시 무효화한다.

## 11. 재시도와 제한

- `429`, `503`은 가능하면 `Retry-After`를 제공한다.
- GET과 멱등성 키가 있는 명령만 지수 백오프와 jitter로 자동 재시도한다.
- `400`, `401`, `403`, `404`, `409`, `412`, `422`는 사용자/상태 변경 없이 자동 재시도하지 않는다.
- 클라이언트 timeout 권장값은 조회 5초, 명령 10초, sync 상태 조회 5초다.

## 12. 식별자와 개인정보

- 공개 resource ID는 UUID이며 의미 있는 user sequence를 포함하지 않는다.
- `RecommendationCard`는 `cardKind`로 구분되는 `INDIVIDUAL | GROUP_ROUTINE` 합타입이다. 그룹 카드는 이름·avatar가 없고 `cohortSize >= 30`, 루틴 구간, 검증일, 신선도를 필수로 가진다.
- 정확 코호트가 30명 미만이면 비금융 생활맥락 tag만 넓히고 금융 수용력 제약은 유지한다. 안전한 상위 코호트도 30명 미만이면 `200 INSUFFICIENT`와 빈 `items`를 반환하며 30명 미만 집계를 만들지 않는다.
- 이때 `fallbackCardKind = GROUP_ROUTINE`은 fallback 분기 메타데이터이고 실제 카드가 아니다. `recommendationState = INSUFFICIENT`, `items = []`가 생성되지 않았음을 확정한다.
- 추천 카드 API에는 source user ID가 존재하지 않는다.
- 타인의 exact KRW, 계좌·카드 식별자, 거래 원문, 상품·종목, 수익률을 반환하지 않는다.
- 사용자 자신의 금융값도 해당 화면에 필요한 최소 필드만 반환한다.
- fixture는 합성 데이터와 예약된 `example.com` 이메일만 사용한다.

## 13. 목표 재설정과 수치 합타입

- `POST /goals/{goalId}/replacement-candidate-sets`는 `PAUSED` 기존 목표를 바꾸지 않고 `purpose = REPLACEMENT`, `replacesGoalId`가 있는 별도 후보를 만든다.
- `POST /goals/{goalId}/replacement`는 후보·동의·ETag를 재검증하고 기존 목표 `CANCELLED / RECALIBRATED`, 새 목표 `ACTIVE`, 연결 퀘스트 종결·생성을 한 트랜잭션으로 커밋한다. 응답의 기존 퀘스트 terminal outcome과 새 퀘스트의 `questId · initialStatus · createdAt`은 명시적이고 두 ID 집합은 서로소다. 새 목표·진행 스냅샷·퀘스트 생성·commit 시각은 `replacementConfirmedAt` 이상이어야 한다. 실패하면 기존 목표는 `PAUSED`로 남는다.
- `UserGoal.state`에는 `CANDIDATE`가 없다. 장기 일시정지나 기준선 변화 전에는 `PAUSED + recalibration.required`로 표현한다.
- quality/UI의 `RECALIBRATION_REQUIRED`는 `recalibration.effectiveState` projection이며 persisted goal state가 아니다.
- `MetricValue`는 `unit` discriminator로 KRW/count/basis point/days/XP를 나눈다. basis point 값은 항상 integer `0..10000`이다.
- `TargetDefinition`은 `kind` discriminator로 `VALUE`, `RANGE`, `COUNT` 중 정확히 하나다. `RANGE`만 범위와 `requiredCount`, `COUNT`만 횟수, `VALUE`만 단일 metric을 가진다.
- `KrwMetricRange`, `CountMetricRange`, `BasisPointMetricRange`는 각각 `x-finmate-ordered-pair`로 최솟값·최댓값 field를 선언하고 `min <= max`를 만족해야 한다. OpenAPI 3.0 schema만으로 일반적인 cross-field 순서를 표현할 수 없으므로 `verify_contracts.py`가 모든 example과 KRW/COUNT/BASIS_POINT inverted negative fixture를 semantic validation으로 거절한다. 이 검사가 공개 executable contract의 일부다.

## 14. AI 후보 추천 결과

- `NewGoalCandidateSet`과 `ReplacementGoalCandidateSet`의 `candidates` 및 모든 목표값·기간·기준선은 결정론적 코드가 만든 정본이다.
- `recommendation`은 같은 payload의 `candidates[].candidateId` 중 하나인 `recommendedCandidateId`와 `reasonCodes`, 숫자 없는 `summary`·`riskNotice`, `recommendationState`만 가진다. 후보 수치나 기간을 복제하는 property와 추가 property는 금지한다.
- AI 출력이 허용 ID, 근거, 숫자 불변, 안전 정책 중 하나라도 실패하면 전체 출력을 폐기하고 코드 기본 순서와 승인 문구를 사용하며 `recommendationState = DETERMINISTIC_FALLBACK`으로 표시한다. 정상 검증 출력만 `AI_GENERATED`다.
- `verify_contracts.py`는 모든 후보 fixture의 ID membership을 검사하고, 존재하지 않는 ID·추가 숫자 property·숫자가 들어간 설명 mutation을 거절한다.

## 15. 계약 변경 절차

1. OpenAPI와 연결 fixture를 같은 변경에서 수정한다.
2. OpenAPI lint, `$ref` 해석, JSON schema validation을 실행한다.
3. 생성 TypeScript/Java client의 breaking diff를 확인한다.
4. additive가 아닌 변경은 새 major 또는 명시적 migration 기간을 설계한다.
5. endpoint별 contract test와 핵심 MVP E2E를 통과한 뒤 병합한다.

## 16. Prism mock 실행

- `examples/*.json`이 요청·응답 예시의 단일 원본이다.
- Prism 5.14는 로컬 `externalValue`를 그대로 응답하지 않으므로 `openapi.yaml`을 직접 mock하지 않는다.
- `build_mock_spec.py`가 68개 `externalValue` 참조를 해당 JSON의 inline `value`로 바꾼 임시 명세를 만든다. 68은 56개 파일 중 일부를 여러 operation에서 재사용한 참조 수다.
- 생성 파일은 `/tmp` 같은 임시 경로에 두고 Git에 커밋하지 않는다.
- named example을 고를 때는 `Prefer: example=<name>`, 오류 상태를 고를 때는 `Prefer: code=<status>, example=<name>`을 사용한다.

```bash
uv run --with-requirements docs/vnext/06-api/requirements.txt \
  python docs/vnext/06-api/build_mock_spec.py \
  --output /tmp/finmate-vnext-openapi.mock.yaml

npx -y @stoplight/prism-cli@5.14.2 mock \
  /tmp/finmate-vnext-openapi.mock.yaml \
  --host 127.0.0.1 --port 4011
```

목 서버는 실제 저장·동시성 제어를 수행하지 않는다. `412`와 멱등 replay는 명시된 예시 계약을 프론트엔드가 구현할 수 있게 할 뿐, 서버 동작 검증은 백엔드 contract·integration test가 담당한다.
