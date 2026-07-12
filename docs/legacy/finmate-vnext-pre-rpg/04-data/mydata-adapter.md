# FinMate vNext MyData 어댑터 계약

## 1. 목적과 MVP 경계

MyData 어댑터는 공급자별 계좌·잔액·거래 응답을 FinMate canonical model로 변환한다. 어댑터는 목표, 스탯, 진행률을 계산하지 않는다. MVP 구현은 버전 고정 합성 fixture를 읽는 `SYNTHETIC` 어댑터이며, 실제 사업자 어댑터가 없어도 아래 계약의 성공·부분 성공·실패·중복 결과를 결정적으로 재현해야 한다.

```text
공급자 인증 또는 합성 fixture 로드
→ 원문 무결성·lossless integer 검증
→ 계좌·거래·잔액 정규화
→ 결정적 중복 winner 선택
→ 분류 규칙·확인 필요 표식
→ sourceDataVersion 커밋
→ 계산 엔진에 SyncCommitted 이벤트 발행
```

## 2. 포트와 완전한 공급자 입력 타입

아래 TypeScript는 경계 IDL이다. JSON integer token은 lossless parser로 `bigint`에 직접 디코딩한다. `JSON.parse`를 거쳐 IEEE-754 값으로 만든 뒤 `bigint`로 바꾸는 구현은 금지한다.

```typescript
type IsoInstant = string;
type IsoDate = string;
type KrwInteger = bigint;
type Nullable<T> = T | null;

interface MyDataProviderAdapter {
  fetchAccounts(request: FetchRequest): Promise<ProviderPage<ProviderAccount>>;
  fetchTransactions(request: FetchRequest): Promise<ProviderPage<ProviderTransaction>>;
  fetchBalances(request: FetchRequest): Promise<ProviderPage<ProviderBalance>>;
  normalizeAccount(input: ProviderAccount, context: NormalizeContext): CanonicalAccount;
  normalizeTransaction(input: ProviderTransaction, context: NormalizeContext): NormalizationResult;
  normalizeBalance(input: ProviderBalance, context: NormalizeContext): CanonicalBalance;
}

interface FetchRequest {
  userId: string;
  connectionId: string;
  providerId: string;
  consentId: string;
  scope: "ACCOUNTS" | "TRANSACTIONS" | "BALANCES";
  providerAccountId: Nullable<string>;
  cursor: Nullable<string>;
  fromInclusive: IsoInstant;
  toExclusive: IsoInstant;
  idempotencyKey: string;
}

interface ProviderPage<T> {
  providerId: string;
  consentId: string;
  responseId: string;
  cursor: Nullable<string>;
  nextCursor: Nullable<string>;
  providerDataAsOf: IsoInstant;
  receivedAt: IsoInstant;
  payloadSha256: string;
  records: T[];
}

interface ProviderWinnerFields {
  responseId: string;
  recordOrdinal: bigint;
  payloadSha256: string;
  receivedAt: IsoInstant;
  providerDataAsOf: IsoInstant;
  providerCreatedAt: Nullable<IsoInstant>;
  providerUpdatedAt: Nullable<IsoInstant>;
}

interface ProviderAccount extends ProviderWinnerFields {
  providerAccountId: string;
  providerAccountTypeCode: string;
  currency: string;
  openedOn: Nullable<IsoDate>;
  closedOn: Nullable<IsoDate>;
  accountStatus: "OPEN" | "CLOSED" | "DORMANT";
}

interface ProviderTransaction extends ProviderWinnerFields {
  providerTransactionId: Nullable<string>;
  providerAccountId: string;
  bookedAt: Nullable<IsoInstant>;
  bookedOn: Nullable<IsoDate>;
  amountKrw: KrwInteger;
  currency: string;
  direction: "CREDIT" | "DEBIT";
  description: string;
  merchantId: Nullable<string>;
  providerTransactionCode: Nullable<string>;
  originalProviderTransactionId: Nullable<string>;
}

interface ProviderBalance extends ProviderWinnerFields {
  providerBalanceId: Nullable<string>;
  providerAccountId: string;
  balanceKrw: KrwInteger;
  currency: string;
  balanceType: "LEDGER" | "AVAILABLE" | "PRINCIPAL" | "OUTSTANDING";
  asOf: IsoInstant;
}

interface NormalizeContext {
  userId: string;
  connectionId: string;
  providerId: string;
  consentId: string;
  syncRunId: string;
  defaultTimezone: "Asia/Seoul";
  canonicalizationVersion: "canonical-v1.0.0";
}
```

모든 `KrwInteger` 입력과 arithmetic intermediate는 `arbitrary-precision` 정수다. canonical 저장 직전에 `0..9223372036854775807` 범위를 확인하고 `checked int64` 변환한다. 범위 초과는 `NUMERIC_OVERFLOW`, 소수 KRW token은 `FRACTIONAL_KRW`로 해당 페이지 전체를 거절한다.

`ProviderWinnerFields`는 fetch 계층이 page metadata와 0-based 배열 위치를 각 레코드에 장식한 값이다. null은 공급자가 해당 생성·수정 시각 또는 ID를 제공하지 않는다고 계약한 경우에만 허용한다.

## 3. 공급자 입력 봉투

```json
{
  "providerId": "provider_demo_bank",
  "consentId": "00000000-0000-4000-8000-000000000101",
  "responseId": "resp_20260712_000042",
  "providerDataAsOf": "2026-07-12T08:55:00+09:00",
  "receivedAt": "2026-07-12T09:00:00+09:00",
  "cursor": "page_2",
  "nextCursor": "page_3",
  "payloadSha256": "342eac55b9460bcfe02dff1f8dbf3129ad1679f950913bcf4d3906cb027ef8f1",
  "records": []
}
```

검증 규칙:

1. `providerId`, `consentId`, `responseId`, `providerDataAsOf`, `receivedAt`, `payloadSha256`, `records`는 필수다.
2. 시각은 UTC 오프셋을 포함한 ISO 8601이어야 한다. 오프셋 없는 시각은 거절한다.
3. `providerDataAsOf <= receivedAt`이어야 한다. 미래 시각은 `PROVIDER_TIME_IN_FUTURE`로 격리한다.
4. 응답 본문 SHA-256이 전송 또는 fixture manifest 해시와 다르면 전체 페이지를 거절한다.
5. 동의 범위에 없는 기관·계좌·기간 데이터는 저장 전에 거절한다.
6. 합성 어댑터도 같은 검증을 거치며 fixture 파일명이나 배열 순서로 계약 검증을 우회하지 않는다.

## 4. Canonical 출력과 메타데이터 배치

Canonical row에는 원천·정규화 계보를 둔다. `calculationVersion`, `dataState`, `lastSyncedAt`, `lastPartialSyncedAt`은 개별 계좌·거래·잔액의 속성이 아니므로 넣지 않는다. 이 값들은 7절의 sync envelope와 계산 스냅샷 또는 API `dataFreshness`에만 둔다.

### 4.1 계좌

```json
{
  "accountId": "00000000-0000-4000-8000-000000000201",
  "userId": "00000000-0000-4000-8000-000000000001",
  "sourceSyncRunId": "00000000-0000-4000-8000-000000000301",
  "providerId": "provider_demo_bank",
  "providerAccountIdHash": "968b21a7f9d14af8b9d680ec79e4971847fbbf870aee6c0618f7f1aeb8d9a321",
  "accountType": "SAVING",
  "currency": "KRW",
  "openedOn": "2024-03-04",
  "closedOn": null,
  "providerCreatedAt": "2024-03-04T00:00:00+09:00",
  "providerUpdatedAt": "2026-07-12T08:54:00+09:00",
  "providerDataAsOf": "2026-07-12T08:55:00+09:00",
  "sourceReceivedAt": "2026-07-12T09:00:00+09:00",
  "sourceResponseId": "resp_20260712_000042",
  "sourceRecordOrdinal": 0,
  "sourcePayloadSha256": "342eac55b9460bcfe02dff1f8dbf3129ad1679f950913bcf4d3906cb027ef8f1",
  "canonicalRecordSha256": "1111111111111111111111111111111111111111111111111111111111111111",
  "sourceDataVersion": "sdv_20260712_085500_7f3a",
  "canonicalizationVersion": "canonical-v1.0.0"
}
```

- 원계좌번호와 `providerAccountId`는 저장하지 않는다. 서비스 비밀키를 사용한 HMAC-SHA-256만 저장한다.
- `accountType`은 `CHECKING`, `SAVING`, `TIME_DEPOSIT`, `LOAN`, `CARD`, `BROKERAGE`, `CASH_EQUIVALENT` 중 하나다.
- 공급자 유형을 매핑할 수 없으면 해당 계좌를 제외하고 동기화를 `PARTIAL_FAILED`로 만든다.

### 4.2 거래

```json canonical-transaction-contract
{
  "fields": [
    "transactionId",
    "userId",
    "accountId",
    "sourceSyncRunId",
    "providerId",
    "providerTransactionIdHash",
    "deduplicationKey",
    "bookedAt",
    "timePrecision",
    "amountKrw",
    "currency",
    "direction",
    "normalizedDescription",
    "merchantKeyHash",
    "classification",
    "classificationConfidenceBps",
    "linkedTransactionId",
    "reviewReasonCodes",
    "providerCreatedAt",
    "providerUpdatedAt",
    "providerDataAsOf",
    "sourceReceivedAt",
    "sourceResponseId",
    "sourceRecordOrdinal",
    "sourcePayloadSha256",
    "canonicalRecordSha256",
    "sourceDataVersion",
    "canonicalizationVersion"
  ],
  "deduplicationSerialization": {
    "format": "LP_UTF8_V1",
    "domainTag": "finmate-transaction-dedup-v1",
    "componentFrame": "<ASCII byte-length>:<raw UTF-8 bytes>",
    "nullFrame": "-1:",
    "withProviderTransactionIdFields": [
      "providerId",
      "accountId",
      "providerTransactionId"
    ],
    "withoutProviderTransactionIdFields": [
      "providerId",
      "accountId",
      "bookedAt",
      "amountKrw",
      "direction",
      "normalizedDescription"
    ]
  },
  "winnerOrder": [
    {
      "field": "providerUpdatedAt",
      "direction": "DESC",
      "nulls": "LAST",
      "comparison": "INSTANT"
    },
    {
      "field": "sourceReceivedAt",
      "direction": "DESC",
      "nulls": "FORBIDDEN",
      "comparison": "INSTANT"
    },
    {
      "field": "sourceResponseId",
      "direction": "ASC",
      "nulls": "FORBIDDEN",
      "comparison": "UNICODE_CODE_POINT"
    },
    {
      "field": "sourceRecordOrdinal",
      "direction": "ASC",
      "nulls": "FORBIDDEN",
      "comparison": "INTEGER"
    },
    {
      "field": "canonicalRecordSha256",
      "direction": "ASC",
      "nulls": "FORBIDDEN",
      "comparison": "LOWERCASE_HEX"
    }
  ]
}
```

```json canonical-transaction-example
{
  "transactionId": "00000000-0000-4000-8000-000000000202",
  "userId": "00000000-0000-4000-8000-000000000001",
  "accountId": "00000000-0000-4000-8000-000000000201",
  "sourceSyncRunId": "00000000-0000-4000-8000-000000000301",
  "providerId": "provider_demo_bank",
  "providerTransactionIdHash": "16dc368a89b428b2485484313ba67a3912ca03f2b2b42429174d6e63b13f67e1",
  "deduplicationKey": "d8bafc951b92d097ab82e70ce39c812e5cf18fcf8257b962d30d5e5c12375c75",
  "bookedAt": "2026-07-11T14:22:00+09:00",
  "timePrecision": "MINUTE",
  "amountKrw": 154000,
  "currency": "KRW",
  "direction": "CREDIT",
  "normalizedDescription": "FINMATE SAVING",
  "merchantKeyHash": null,
  "classification": "SAVING_CONTRIBUTION",
  "classificationConfidenceBps": 10000,
  "linkedTransactionId": null,
  "reviewReasonCodes": [],
  "providerCreatedAt": "2026-07-11T14:22:01+09:00",
  "providerUpdatedAt": "2026-07-11T14:22:01+09:00",
  "providerDataAsOf": "2026-07-12T08:55:00+09:00",
  "sourceReceivedAt": "2026-07-12T09:00:00+09:00",
  "sourceResponseId": "resp_20260712_000042",
  "sourceRecordOrdinal": 4,
  "sourcePayloadSha256": "342eac55b9460bcfe02dff1f8dbf3129ad1679f950913bcf4d3906cb027ef8f1",
  "canonicalRecordSha256": "2222222222222222222222222222222222222222222222222222222222222222",
  "sourceDataVersion": "sdv_20260712_085500_7f3a",
  "canonicalizationVersion": "canonical-v1.0.0"
}
```

- `amountKrw`는 `>= 0` 정수고 `currency`는 `KRW`다. 입·출금 부호는 `direction`으로 표현한다.
- 외화 원거래는 제한 저장할 수 있지만 목표 계산에 쓰지 않는다. 공급자가 확정한 원화 청구액이 있을 때만 `amountKrw`를 만든다.
- 날짜만 있으면 `Asia/Seoul` 정오로 저장하고 `timePrecision = DATE`로 표시한다. 중복 휴리스틱은 같은 달력일 전체를 비교한다.
- `classificationConfidenceBps < 8000`, 규칙 충돌, 가능한 중복은 `classification = REVIEW_REQUIRED`다. 그 거래를 사용하는 계산 envelope가 `dataState = NEEDS_REVIEW`가 되는 것이며 거래 row에 `dataState`를 저장하지 않는다.

### 4.3 잔액

```json
{
  "balanceId": "00000000-0000-4000-8000-000000000203",
  "userId": "00000000-0000-4000-8000-000000000001",
  "accountId": "00000000-0000-4000-8000-000000000201",
  "sourceSyncRunId": "00000000-0000-4000-8000-000000000301",
  "providerId": "provider_demo_bank",
  "providerBalanceIdHash": "5ca32b0a2b8bdd7a436f2dd56d0cc0b6e4fac7e0eb2e6b92e698f26ce06f449b",
  "balanceKrw": 820000,
  "currency": "KRW",
  "balanceType": "AVAILABLE",
  "asOf": "2026-07-12T08:55:00+09:00",
  "providerCreatedAt": null,
  "providerUpdatedAt": "2026-07-12T08:55:00+09:00",
  "providerDataAsOf": "2026-07-12T08:55:00+09:00",
  "sourceReceivedAt": "2026-07-12T09:00:00+09:00",
  "sourceResponseId": "resp_20260712_000042",
  "sourceRecordOrdinal": 1,
  "sourcePayloadSha256": "342eac55b9460bcfe02dff1f8dbf3129ad1679f950913bcf4d3906cb027ef8f1",
  "canonicalRecordSha256": "3333333333333333333333333333333333333333333333333333333333333333",
  "sourceDataVersion": "sdv_20260712_085500_7f3a",
  "canonicalizationVersion": "canonical-v1.0.0"
}
```

비상금은 사용자가 지정한 `CHECKING | SAVING | CASH_EQUIVALENT` 계좌의 `AVAILABLE` 잔액만 사용한다.

## 5. 정규화와 결정적 중복 제거

### 5.1 문자열

```text
normalizedDescription
  = Unicode NFKC
  → 제어문자 제거
  → 앞뒤 공백 제거
  → 연속 공백 하나로 축소
  → 영문 대문자 변환
  → 120자에서 절단
```

계좌번호, 전화번호, 이메일 형태는 `[REDACTED]`로 치환한다. 공개 카드와 AI 입력에는 정규 설명도 전달하지 않는다.

### 5.2 식별자, 키, winner

`deduplicationKey`는 4.2 manifest의 `LP_UTF8_V1`만 사용한다. `LP(value)`는 canonical scalar text의 UTF-8 byte 길이를 선행 0 없는 ASCII 십진수로 쓰고 `:`와 raw UTF-8 byte를 붙이며 null은 정확히 `-1:`이다. 정수는 선행 0 없는 10진수, UUID는 lowercase hyphenated form, 시각은 UTC `Z` RFC 3339 canonical form이다.

```text
deduplicationKey = SHA-256(
  LP("finmate-transaction-dedup-v1") ||
  LP(field1) || ... || LP(fieldN)
)
```

`providerTransactionId`가 있으면 manifest의 `withProviderTransactionIdFields`, 없으면 `withoutProviderTransactionIdFields`를 정확한 순서로 사용한다. separator 연결과 JSON object key 순서 의존은 금지한다.

같은 정확 키의 winner는 다음 우선순위를 한 번만 적용한다.

1. `providerUpdatedAt` 최신. null은 가장 오래된 값으로 취급한다.
2. 같으면 `sourceReceivedAt` 최신.
3. 같으면 `sourceResponseId` Unicode code point 사전순 최소.
4. 같으면 `sourceRecordOrdinal` 최소.
5. 같으면 `canonicalRecordSha256` lowercase hex 사전순 최소.

winner 입력인 `providerUpdatedAt`, `sourceReceivedAt`, `sourceResponseId`, `sourceRecordOrdinal`, `sourcePayloadSha256`, `canonicalRecordSha256`를 모두 보존한다. 위 다섯 tie-break 값이 모두 같은데 정규 직렬화가 다르면 hash collision 또는 직렬화 위반이므로 둘 다 격리하고 `CONFLICTING_DUPLICATE`로 처리한다. 배열 도착 순서, 데이터베이스 물리 순서, 현재 서버 시각은 winner 결정에 쓰지 않는다.

서로 다른 키라도 같은 계좌·방향·금액·설명이고 시각 차이가 `PT5M` 이하면 `POSSIBLE_DUPLICATE`로 표시하고 계산에서 제외한다. Canonical source record 정렬은 `bookedAt`, `accountId`, `deduplicationKey` 오름차순이며 이 순서로 `sourceDataVersion` 해시를 만든다.

### 5.3 내부이체와 환불

동일 사용자 계좌 사이에서 방향 반대, 같은 금액, 시각 차이 `<= PT10M`, 이체 근거가 모두 참이면 `INTERNAL_TRANSFER`로 연결한다. 날짜 정밀도만 있으면 같은 달력일을 사용한다. 후보가 여러 개면 시각 차이, 계좌 UUID, 거래 UUID 오름차순으로 하나를 고른다. 동률이나 분할 이체는 `REVIEW_REQUIRED`다.

환불은 공급자 원거래 참조를 우선한다. 참조가 없으면 같은 계좌·가맹점, 반대 방향, 환불액이 원거래액 이하, 원거래 뒤 `P90D` 안인 가장 가까운 이전 거래 하나를 연결한다. 같은 거리 후보가 둘 이상이면 확인 필요다.

### 5.4 대출·만기·증권 이동

- 대출 실행금과 원금 상환은 `LOAN_PRINCIPAL`이다. `CREDIT` 실행금은 소득·필수지출에서 제외하고, `DEBIT` 원금 상환은 수용력 기준의 필수지출에 canonical 거래당 정확히 한 번 포함한다. 원금 상환은 재량지출·소비절감 목표에서는 제외한다. 공급자가 분리한 이자만 `ESSENTIAL_EXPENSE`가 될 수 있다.
- 만기 입금과 동일 사용자 예적금 재예치가 ISO `P3D` **달력일** 안에 연결되고 재예치액이 만기 원금 이하이면 `MATURITY_REINVESTMENT`로 제외한다. 초과 납입분만 새 저축 후보로 둔다.
- 증권 연결계좌와 증권계좌 사이 단순 이동은 `BROKERAGE_TRANSFER`다. 매수·매도·배당·평가손익은 목표·XP 입력으로 보내지 않는다.

## 6. `sourceDataVersion`과 커밋

```text
sourceDataVersion =
  "sdv_" + format(providerDataAsOf, "yyyyMMdd_HHmmss") + "_" +
  first8(SHA-256(canonicalizationVersion + sortedCanonicalRecordHashes))
```

커밋 절차:

1. 동의가 여전히 `ACTIVE`인지 다시 확인한다.
2. 모든 페이지 해시와 cursor 연속성을 검증한다.
3. 정규 레코드와 분류 결정을 임시 영역에 쓴다.
4. 중복 winner를 선택하고 정렬된 record hash와 `canonicalizationVersion`으로 `sourceDataVersion`을 만든다.
5. 성공 범위의 계좌·거래·잔액, `SyncScopeResult`, `SyncRun`, outbox event를 한 트랜잭션으로 커밋한다.
6. 모든 요청 범위가 성공한 `SUCCEEDED`면 `lastSyncedAt = min(requiredScope.providerDataAsOf)`와 `lastFullSyncRunId`를 전진시킨다.
7. 일부 범위만 성공한 `PARTIAL_FAILED`면 `lastPartialSyncedAt = min(committedScope.providerDataAsOf)`와 `lastPartialSyncRunId`만 전진시킨다. 공개 `lastSyncedAt`은 이전 완전 성공 값 그대로다.

같은 `sourceDataVersion`이 이미 커밋됐으면 새 row나 이벤트를 만들지 않고 기존 sync 결과를 반환한다.

`SUCCEEDED | PARTIAL_FAILED | FAILED | BLOCKED`가 된 `SyncRun`은 terminal이며 상태, `finishedAt`, 범위 결과를 다시 쓰지 않는다. 실패 범위 재시도는 backoff 뒤 새 `syncRunId`, `syncStatus = REQUESTED`, `retryOfSyncRunId = 이전 syncRunId`로 실행한다. scheduler/connection의 `IDLE` readiness는 실행 row에 저장하지 않는다.

## 7. Sync envelope와 데이터 상태

```json
{
  "syncRunId": "00000000-0000-4000-8000-000000000301",
  "syncStatus": "SUCCEEDED",
  "sourceDataVersion": "sdv_20260712_085500_7f3a",
  "canonicalizationVersion": "canonical-v1.0.0",
  "calculationVersion": "goal-calc-1.0.0",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "lastPartialSyncedAt": null,
  "calculatedAt": "2026-07-12T09:00:01+09:00"
}
```

`lastPartialSyncedAt` null은 이 연결에 부분 커밋 이력이 없을 때만 명시적으로 허용한다. `lastSyncedAt` null은 최초 완전 성공 전 `INSUFFICIENT` 응답에서만 허용한다.

데이터 상태 입력은 공개 `lastSyncedAt`, 동의 `P7D | P30D`, 현재 시각, 최소 데이터, 확인 필요 거래, 금융 증거 대기 여부다.

```text
dueAt = lastSyncedAt + syncInterval
staleAt = dueAt + P2D

if reviewRequired then NEEDS_REVIEW
else if minimumDataMissing or lastSyncedAt missing then INSUFFICIENT
else if now >= staleAt then STALE
else if pendingFinancialEvidence then PENDING
else FRESH
```

`lastPartialSyncedAt`은 상태 판정의 `lastSyncedAt`을 대신하지 않는다. `PENDING`은 이전 확정값을 유지한다. `STALE`, `INSUFFICIENT`, `NEEDS_REVIEW`는 새 정량 후보 확정과 목표·레이드·금융 퀘스트 진행 갱신을 차단한다.

## 8. 이벤트 계약

```json
{
  "eventType": "mydata.sync.committed.v1",
  "eventId": "evt_01J2ABCDEF0123456789XYZ",
  "userId": "00000000-0000-4000-8000-000000000001",
  "connectionId": "00000000-0000-4000-8000-000000000101",
  "consentId": "00000000-0000-4000-8000-000000000102",
  "syncRunId": "00000000-0000-4000-8000-000000000301",
  "syncStatus": "SUCCEEDED",
  "sourceDataVersion": "sdv_20260712_085500_7f3a",
  "canonicalizationVersion": "canonical-v1.0.0",
  "providerDataAsOf": "2026-07-12T08:55:00+09:00",
  "calculationVersion": "goal-calc-1.0.0",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "lastPartialSyncedAt": null,
  "occurredAt": "2026-07-12T09:00:01+09:00"
}
```

파티션 키는 `userId`다. 소비자는 `eventId`로 멱등 처리한다. 같은 사용자의 이벤트는 outbox commit 순서를 보존한다.

## 9. 오류와 재시도

| 코드 | 범위 | 재시도 | 처리 |
| --- | --- | --- | --- |
| `CONSENT_INACTIVE` | 실행 | 안 함 | `BLOCKED`, 새 동의 필요 |
| `PROVIDER_AUTH_FAILED` | 기관 | 재인증 전 안 함 | `SUSPENDED`, 사용자 안내 |
| `PROVIDER_RATE_LIMITED` | 페이지 | 함 | `PT5M`, `PT30M`, `PT2H` |
| `PROVIDER_TIMEOUT` | 페이지 | 함 | 진행 중 실행에서는 같은 cursor와 page idempotency key; 실행 종결 뒤에는 새 `syncRunId` |
| `PAYLOAD_HASH_MISMATCH` | 페이지 | 한 번 | 다시 실패하면 `FAILED`와 보안 로그 |
| `PROVIDER_TIME_IN_FUTURE` | 레코드 | 안 함 | 격리, 계산 envelope `NEEDS_REVIEW` |
| `FRACTIONAL_KRW` | 페이지 | 안 함 | 페이지 거절, 공급자 매핑 수정 |
| `NUMERIC_OVERFLOW` | 페이지 | 안 함 | 페이지 거절, 운영 경보 |
| `UNSUPPORTED_ACCOUNT_TYPE` | 계좌 | 안 함 | 계좌 제외, `PARTIAL_FAILED` |
| `CONFLICTING_DUPLICATE` | 거래 | 안 함 | 둘 다 계산 제외, 사용자 확인 |
| `SCHEMA_VALIDATION_FAILED` | 페이지 | 안 함 | 원문 격리, `FAILED` |

세 번째 자동 재시도 후에도 실패하면 다음 동의 전송 주기까지 기다린다. 사용자가 새로고침해도 공급자 제한을 우회하지 않는다.

## 10. 보안과 관측성

- 접근 token은 secret store에 두고 로그·원천 record·오류 메시지에 남기지 않는다.
- 원문은 envelope encryption을 적용하고 정규화 성공 후 `P7D`에 삭제한다.
- 계좌번호, 사용자 이름, 상세 설명, 원본 사용자 연결 키를 metric label로 사용하지 않는다.
- 필수 metric은 `sync_run_count{provider,status}`, `sync_duration_seconds`, `normalized_record_count{type}`, `review_required_count{reason}`, `staleness_seconds`다.
- 샘플 log는 `syncRunId`, `providerId`, `status`, `errorCode`, record count, 시각만 포함한다.
- 공개 동의 `WITHDRAWN` 이벤트는 추천 인덱스 삭제 완료까지 추적하며 `P1D`를 넘으면 운영 경보를 발생시킨다.
