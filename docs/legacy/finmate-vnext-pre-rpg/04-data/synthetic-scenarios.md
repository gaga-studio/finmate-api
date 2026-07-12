# FinMate vNext 합성 시나리오

## 1. 픽스처 규칙

이 문서의 인물·기관·계좌·거래는 모두 합성 데이터다. 테스트 시계와 계산 버전을 고정한다.

```json
{
  "now": "2026-07-12T09:00:00+09:00",
  "timezone": "Asia/Seoul",
  "calculationVersion": "goal-calc-1.0.0",
  "defaultSyncInterval": "P7D",
  "freshnessGrace": "P2D",
  "currency": "KRW",
  "ratioUnit": "BASIS_POINT"
}
```

모든 금액은 정수 `KRW`, 모든 비율은 정수 basis point다. 각 기대 결과는 계산 엔진·골든·시나리오·영속화가 공유하는 **내부** `` ```json calculation-envelope ``로 표시하고 `calculationVersion`, `sourceDataVersion`, `dataState`, `lastSyncedAt`, `calculatedAt`, `result`만 최상위에 두어야 통과한다. 이 봉투는 공개 HTTP 응답 shape가 아니다. 공개 API는 OpenAPI의 domain payload와 그 스키마가 요구하는 `dataFreshness`·`calculationVersion` 메타데이터를 사용하며 공통 최상위 `result` wrapper를 추가하지 않는다.

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

## 2. 시나리오 인덱스

| ID | 구분 | 검증 대상 | 기대 핵심 결과 |
| --- | --- | --- | --- |
| `S01` | 정상 | 정규소득·여윳돈·저축 목표 | 표준 `1400 bp`, `154000 KRW` |
| `S02` | 경계 | 여윳돈 0 이하 | 정량 후보 없음, 행동형만 |
| `S03` | 정상 | 불규칙소득·일회성 입금 | 6개월 중앙값 `1250000 KRW` |
| `S04` | 경계 | 내부이체·환불·대출·재예치 | 제외·상계 후 이중 집계 없음 |
| `S05` | 경계 | 정확 코호트 30명 미만 | 안전한 상위 코호트면 `GROUP_ROUTINE`, 아니면 카드 없음 |
| `S06` | 경계 | 백분위·안전 범위 제한 | 출처 거절 또는 참고값 제한 |
| `S07` | 경계 | 오래된 데이터·반영 대기 | `STALE`과 `PENDING` 구분 |
| `S08` | 경계 | 기준값과 목표값 동일 | 후보 폐기 |
| `S09` | 경계 | 기준선 20% 변화 | `NEEDS_RECALIBRATION` |
| `S10` | 경계 | 후보 만료와 멱등성 | 확정 거절, 목표 미생성 |
| `S11` | 경계 | 일시정지 30일 | 정확히 30일 재개, 초과 시 별도 교체 후보 |
| `S12` | 경계 | AI 숫자·ID 변조 | AI 출력 폐기, 코드 순서 사용 |
| `S13` | 개인정보 | 공개 동의 철회 | 즉시 조회 제외, `P1D` 내 파생 삭제 |
| `S14` | 회귀 | 완료 후 현재 지표 하락 | 완료 기록·해금 유지 |
| `S15` | 경계 | 중복·분류 충돌 | `NEEDS_REVIEW`, 계산 중지 |
| `S16` | 정상·경계 | 레이드 단계 경계 | `3300`, `6600`, `10000 bp` 정확 전이 |
| `S17` | 회귀 | 자동저축 이중 반영 | 설정 XP와 입금 지표 분리 |
| `S18` | 안전 | 투자 판단 게임화 | XP·보상·축하 효과 0 |

## 3. 상세 픽스처와 기대값

### S01. 정규소득과 저축률 증가

입력:

```json
{
  "userId": "usr_s01",
  "incomeRegularity": "REGULAR",
  "completedMonths": [
    {"month": "2026-04", "earnedIncomeKrw": 2700000, "essentialExpenseClassifiedKrw": 1280000, "loanPrincipalKrw": 200000},
    {"month": "2026-05", "earnedIncomeKrw": 2500000, "essentialExpenseClassifiedKrw": 1310000, "loanPrincipalKrw": 200000},
    {"month": "2026-06", "earnedIncomeKrw": 2600000, "essentialExpenseClassifiedKrw": 1300000, "loanPrincipalKrw": 200000}
  ],
  "baselineSavingRateBps": 800,
  "sourceRoutineValueBps": 2200,
  "groupPercentilesBps": {"p10": 900, "p25": 1200, "p75": 2000, "p90": 2400},
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "sourceDataVersion": "sdv_s01"
}
```

계산:

```text
baselineMonthlyIncomeKrw = median(2700000, 2500000, 2600000) = 2600000
monthlyEssentialExpenseKrw = ESSENTIAL_EXPENSE + LOAN_PRINCIPAL
monthlyEssentialExpenseKrw = [1280000+200000, 1310000+200000, 1300000+200000]
baselineEssentialExpenseKrw = median(1480000, 1510000, 1500000) = 1500000
surplusKrw = 1100000
referenceValueBps = clamp(2200, p25=1200, p75=2000) = 2000
LIGHT = 800 + (2000 - 800) × 2500 / 10000 = 1100
STANDARD = 1400
CHALLENGE = 1700
STANDARD targetAmountKrw = 1100000 × 1400 / 10000 = 154000
```

기대:

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s01",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "baselineMonthlyIncomeKrw": 2600000,
    "monthlyEssentialExpenseKrw": [1480000, 1510000, 1500000],
    "baselineEssentialExpenseKrw": 1500000,
    "loanPrincipalIncludedExactlyOnce": true,
    "surplusKrw": 1100000,
    "candidates": [
      {"difficulty": "LIGHT", "targetValueBps": 1100, "targetAmountKrw": 121000},
      {"difficulty": "STANDARD", "targetValueBps": 1400, "targetAmountKrw": 154000},
      {"difficulty": "CHALLENGE", "targetValueBps": 1700, "targetAmountKrw": 187000}
    ]
  }
}
```

표준 목표에서 현재 저축률이 `1100 bp`이면 진행률은 `(1100-800)/(1400-800) = 5000 bp`다.

### S02. 여윳돈이 0 이하

입력은 기준 월소득 `1800000 KRW`, 필수지출 `1850000 KRW`, 마지막 동기화 `2026-07-12T08:55:00+09:00`이다.

기대:

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s02",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "surplusKrw": -50000,
    "quantitativeCandidates": [],
    "startActions": [
      "RECORD_BUDGET",
      "REVIEW_FIXED_COSTS",
      "VIEW_SUPPORT_INFORMATION"
    ],
    "reasonCode": "NON_POSITIVE_SURPLUS"
  }
}
```

저축률·소비율·투자율 필드는 반환하지 않는다. `0`으로 꾸며 반환하지 않는다.

### S03. 불규칙소득과 일회성 고액 입금

유효 완료월 소득은 `900000, 3200000, 1100000, 1400000, 1300000, 1200000 KRW`다. 별도 증여 `8000000 KRW`는 `EARNED_INCOME`이 아니므로 제외한다.

```text
정렬 = 900000, 1100000, 1200000, 1300000, 1400000, 3200000
중앙값 = roundHalfUp((1200000 + 1300000) / 2) = 1250000 KRW
```

필수지출 중앙값이 `900000 KRW`이면 여윳돈은 `350000 KRW`다. 유효 소득월이 4개 미만이면 같은 입력 창에서도 `dataState = INSUFFICIENT`이고 정량 후보는 없다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s03",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "baselineMonthlyIncomeKrw": 1250000,
    "baselineEssentialExpenseKrw": 900000,
    "surplusKrw": 350000,
    "excludedIncomeKrw": 8000000
  }
}
```

### S04. 거래 제외와 환불 상계

```json
{
  "transactions": [
    {"id": "t01", "amountKrw": 2600000, "direction": "CREDIT", "classification": "EARNED_INCOME"},
    {"id": "t02", "amountKrw": 500000, "direction": "DEBIT", "classification": "INTERNAL_TRANSFER", "linkedTransactionId": "t03"},
    {"id": "t03", "amountKrw": 500000, "direction": "CREDIT", "classification": "INTERNAL_TRANSFER", "linkedTransactionId": "t02"},
    {"id": "t04", "amountKrw": 120000, "direction": "DEBIT", "classification": "DISCRETIONARY_EXPENSE"},
    {"id": "t05", "amountKrw": 30000, "direction": "CREDIT", "classification": "REVERSAL", "linkedTransactionId": "t04"},
    {"id": "t06", "amountKrw": 5000000, "direction": "CREDIT", "classification": "LOAN_PRINCIPAL"},
    {"id": "t07", "amountKrw": 2000000, "direction": "CREDIT", "classification": "MATURITY_REINVESTMENT", "linkedTransactionId": "t08"},
    {"id": "t08", "amountKrw": 2000000, "direction": "DEBIT", "classification": "MATURITY_REINVESTMENT", "linkedTransactionId": "t07"},
    {"id": "t09", "amountKrw": 154000, "direction": "CREDIT", "classification": "SAVING_CONTRIBUTION"},
    {"id": "t10", "amountKrw": 200000, "direction": "DEBIT", "classification": "LOAN_PRINCIPAL"}
  ]
}
```

기대 집계는 소득 `2600000 KRW`, 재량지출 `90000 KRW`, 저축성 순유입 `154000 KRW`다. 내부이체 `1000000 KRW`, 대출 실행 `5000000 KRW`, 만기·재예치 `4000000 KRW`는 어느 성과에도 포함하지 않는다. 원금 상환 `t10`은 재량지출·저축 성과에서는 제외하지만 수용력 필수지출에는 `200000 KRW`로 정확히 한 번 포함한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s04",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "earnedIncomeKrw": 2600000,
    "discretionaryExpenseKrw": 90000,
    "affordabilityEssentialExpenseKrw": 200000,
    "affordabilityIncludedTransactionIds": ["t10"],
    "netSavingInflowKrw": 154000,
    "discretionaryAndSavingExcludedTransactionIds": ["t02", "t03", "t06", "t07", "t08", "t10"]
  }
}
```

### S05. 추천 그룹 최소 표본

정확 코호트의 `eligibleProfileCount = 29`이면 개인 카드는 만들지 않는다. 소득 규칙성 `REGULAR`, 여윳돈 구간 `1000000..1499999 KRW`, 목표 영역 `SAVING`은 금융 수용력 제약으로 고정하고 비금융 `MONTHLY_RENT`, `LIVES_ALONE` 태그만 `cohort-v1.2.0` 일반화 순서대로 넓힌다.

첫 안전 상위 코호트가 34명이면 다음 집계 카드 한 개를 반환한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s05_exact_cohort",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "items": [
      {
        "cardKind": "GROUP_ROUTINE",
        "cohortSize": 34,
        "generalizedContextTags": ["RENTING"],
        "routineBands": [{"unit": "BASIS_POINT", "min": 1200, "max": 1800}],
        "validatedOn": "2026-07-12"
      }
    ],
    "individualCardCreated": false,
    "fallbackCardKind": "GROUP_ROUTINE",
    "recommendationState": "GROUP_ROUTINE_AVAILABLE",
    "financialCapacityConstraintsPreserved": true
  }
}
```

모든 허용 상위 코호트도 29명이면 `GROUP_ROUTINE`을 만들지 않는다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s05_no_safe_cohort",
  "dataState": "INSUFFICIENT",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "items": [],
    "individualCardCreated": false,
    "groupCardCreated": false,
    "fallbackCardKind": "GROUP_ROUTINE",
    "recommendationState": "INSUFFICIENT",
    "reasonCode": "SAFE_COHORT_TOO_SMALL"
  }
}
```

### S06. 백분위와 안전 범위

40명의 정렬 지표가 `500, 600, ..., 4400 bp`일 때 nearest-rank는 `P10=800`, `P25=1400`, `P75=3400`, `P90=4000 bp`다.

- 출처 `4500 bp`: `P90` 밖이므로 카드 자체를 거절한다.
- 출처 `3800 bp`: 카드 자격은 통과한다. 참고값은 `P75`에서 `3400 bp`로 제한한다.
- 템플릿 안전 최대가 `3000 bp`이면 최종 참고값은 `3000 bp`다.

모든 단계에서 원본 `3800 bp`는 클라이언트에 반환하지 않는다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s06",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "eligibleProfileCount": 40,
    "sourceCardAllowed": true,
    "groupLimitedReferenceBps": 3400,
    "finalReferenceBps": 3000,
    "outlierSourceCardAllowed": false
  }
}
```

### S07. 오래된 데이터와 반영 대기

동의 주기가 `P7D`, 유예가 `P2D`일 때:

| `lastSyncedAt` | `now` | 대기 행동 | 기대 `dataState` |
| --- | --- | --- | --- |
| `2026-07-01T09:00:00+09:00` | `2026-07-10T09:00:00+09:00` | 없음 | `STALE` |
| `2026-07-12T08:55:00+09:00` | `2026-07-12T09:00:00+09:00` | 저축 입금 확인 대기 | `PENDING` |
| `2026-07-12T08:55:00+09:00` | `2026-07-12T09:00:00+09:00` | 없음 | `FRESH` |

`STALE`과 `PENDING` 모두 이전 진행률을 유지한다. `PENDING`은 새 행동이 원천에 나타난 뒤 `FRESH`로 전환하며, 행동 기록만으로 진행률을 올리지 않는다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s07_stale",
  "dataState": "STALE",
  "lastSyncedAt": "2026-07-01T09:00:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "case": "STALE_AFTER_GRACE",
    "syncStatus": "SUCCEEDED",
    "progressFrozen": true
  }
}
```

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s07_pending",
  "dataState": "PENDING",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "case": "VERIFICATION_PENDING",
    "syncStatus": "SUCCEEDED",
    "progressFrozen": true
  }
}
```

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s07_fresh",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "case": "FULL_SYNC_FRESH",
    "syncStatus": "SUCCEEDED",
    "progressFrozen": false
  }
}
```

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s07_partial",
  "dataState": "STALE",
  "lastSyncedAt": "2026-07-01T09:00:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "case": "PARTIAL_SYNC_DOES_NOT_ADVANCE_PUBLIC_FRESHNESS",
    "syncRunId": "00000000-0000-4000-8000-000000000701",
    "syncStatus": "PARTIAL_FAILED",
    "syncRunTerminal": true,
    "retryCreatesNewSyncRunId": "00000000-0000-4000-8000-000000000702",
    "schedulerReadinessAfterCompletion": "IDLE",
    "previousPublicLastSyncedAt": "2026-07-01T09:00:00+09:00",
    "lastPartialSyncedAt": "2026-07-12T08:58:00+09:00",
    "publicLastSyncedAtAdvanced": false,
    "progressFrozen": true
  }
}
```

### S08. 기준값과 목표값이 같은 후보

기준 `1400 bp`, 그룹 제한 참고값 `1450 bp`, 반올림 단위 `100 bp`인 증가형 LIGHT 후보는 반올림 후 `1400 bp`다. 기준과 같으므로 저장하지 않는다. 세 난이도가 모두 같아지면 정량 후보 응답은 비고 `reasonCode = NO_MEANINGFUL_IMPROVEMENT`와 행동형 대안을 반환한다.

손상된 활성 목표에서 분모가 0이면 `0 bp`나 `10000 bp`를 계산하지 않고 다음을 남긴다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s08",
  "dataState": "NEEDS_REVIEW",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "changeReason": "INVALID_TARGET_DENOMINATOR"
  }
}
```

### S09. 기준선 20% 변화

확정 소득 `2600000 KRW`, 새 소득 `2080000 KRW`이면 절대 차이 `520000 KRW`, 변화 `2000 bp`다. 정확히 임계값이므로 목표값을 바꾸지 않고 `UserGoal.state = PAUSED`, `recalibrationRequired = true`로 둔다. 레이드 읽기 모델은 `NEEDS_RECALIBRATION`이며 별도 교체 후보 세트를 만들 수 있다.

필수지출도 같은 식으로 독립 평가하며 둘 중 하나만 임계값 이상이어도 재설정한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s09",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "goalState": "PAUSED",
    "targetAutomaticallyChanged": false,
    "recalibrationRequired": true,
    "recalibrationReason": "BASELINE_CHANGED_2000_BPS_OR_MORE",
    "raidState": "NEEDS_RECALIBRATION",
    "replacementCandidateSetSeparate": true
  }
}
```

### S10. 후보 만료와 멱등성

후보 생성 `2026-07-11T09:00:00+09:00`, `expiresAt = 2026-07-12T09:00:00+09:00`이다. 확정 요청 시각이 정확히 만료 시각이면 `now < expiresAt`가 거짓이므로 `CANDIDATE_EXPIRED`를 반환하고 목표를 생성하지 않는다.

같은 `idempotencyKey`로 재요청하면 같은 오류를 반환한다. 같은 키에 다른 `candidateId`를 보내면 `IDEMPOTENCY_CONFLICT`다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s10",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "errorCode": "CANDIDATE_EXPIRED",
    "goalCreated": false,
    "replayedErrorMatchesOriginal": true,
    "differentBodyErrorCode": "IDEMPOTENCY_CONFLICT"
  }
}
```

### S11. 일시정지 기간 경계

| `pausedAt` | `resumeRequestedAt` | 기준선 변화 | 기대 |
| --- | --- | ---: | --- |
| `2026-06-12T09:00:00+09:00` | `2026-07-12T09:00:00+09:00` | `1999 bp` | 기존 목표 `ACTIVE` 재개 |
| `2026-06-12T08:59:59+09:00` | `2026-07-12T09:00:00+09:00` | `0 bp` | 기존 목표 `PAUSED`, 별도 교체 후보 필요 |
| `2026-07-01T09:00:00+09:00` | `2026-07-12T09:00:00+09:00` | `2000 bp` | 기존 목표 `PAUSED`, 별도 교체 후보 필요 |

일시정지 중 진행률과 `highestProgressBps`는 변하지 않는다.

템플릿 비활성과 필수 동의 변경도 같은 `PAUSED + recalibrationRequired` 결과를 만들며 각각 `TEMPLATE_INACTIVE`, `REQUIRED_CONSENT_CHANGED`를 기록한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s11",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "exactP30DGoalState": "ACTIVE",
    "overP30DGoalState": "PAUSED",
    "overP30DRecalibrationReason": "PAUSE_EXCEEDED_P30D",
    "thresholdGoalState": "PAUSED",
    "thresholdRecalibrationReason": "BASELINE_CHANGED_2000_BPS_OR_MORE",
    "templateInactiveRecalibrationReason": "TEMPLATE_INACTIVE",
    "requiredConsentChangedRecalibrationReason": "REQUIRED_CONSENT_CHANGED",
    "replacementCandidatesRequired": true,
    "userGoalTransitionedToCandidate": false,
    "linkedQuestStateWhilePaused": "PAUSED",
    "linkedQuestXpAwardedWhilePaused": 0
  }
}
```

### S12. AI 출력 검증 실패

코드 후보 ID가 `cand_light`, `cand_standard`, `cand_challenge`이고 표준 금액이 `154000 KRW`인데 AI가 다음을 반환한다.

```json
{
  "recommendedCandidateId": "cand_invented",
  "summary": "표준 목표는 160000원입니다.",
  "riskNotice": "부담되면 조정하세요."
}
```

ID가 없고 금액도 다르므로 전체 AI 출력을 폐기한다. 화면은 코드 기본 순서 `STANDARD`, `LIGHT`, `CHALLENGE`와 저장된 정확 금액을 표시한다. 목표 확정은 계속 가능하며 AI 감사 로그 `validationStatus = REJECTED_UNKNOWN_ID_AND_NUMBER_MISMATCH`를 남긴다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s12",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "aiOutputAccepted": false,
    "fallbackCandidateOrder": ["STANDARD", "LIGHT", "CHALLENGE"],
    "goalConfirmationAvailable": true,
    "validationStatus": "REJECTED_UNKNOWN_ID_AND_NUMBER_MISMATCH"
  }
}
```

### S13. 공개 동의 철회

철회 시각 `2026-07-12T09:00:00+09:00`에:

1. `ShareConsent.state = WITHDRAWN`과 동시에 새 추천 조회에서 카드를 제외한다.
2. 카드 캐시와 파생 인덱스의 `purgeAt`은 늦어도 `2026-07-13T09:00:00+09:00`이다.
3. 이미 타인이 확정한 목표에서는 `sourceProfileKey`와 `cardId` 연결을 제거하고 `goalTemplateId`, `routineType`만 유지한다.
4. 철회자의 개인 목표·여정 기록은 개인 보존 정책에 따라 유지한다.

파생 삭제가 `P1D`를 넘으면 운영 경보가 발생한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s13",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "shareConsentState": "WITHDRAWN",
    "excludedFromNewQueries": true,
    "derivedDataRemovalDueAt": "2026-07-13T09:00:00+09:00",
    "existingGoalSourceLinkRemoved": true
  }
}
```

### S14. 완료 후 현재 지표 하락

목표가 `2026-07-01T09:00:00+09:00`에 `10000 bp`로 완료된 뒤 현재 진단 지표가 목표 환산 `7000 bp`로 낮아진다.

- `UserGoal.state = COMPLETED` 유지
- 완료 스냅샷 `currentProgressBps = highestProgressBps = 10000` 유지
- `RaidProgress.unlockedStage = 3` 유지
- 현재 리포트에 `currentDiagnosticProgressBps = 7000`을 별도 표시
- 실패, 강등, 보스 회복, 보상 회수 없음

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s14",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "goalState": "COMPLETED",
    "raidState": "GOAL_COMPLETED",
    "reportViewChangedRaidState": false,
    "completedProgressBps": 10000,
    "highestProgressBps": 10000,
    "unlockedStage": 3,
    "currentDiagnosticProgressBps": 7000
  }
}
```

### S15. 중복과 분류 충돌

서로 다른 공급자 거래 ID 두 개가 같은 계좌, 같은 방향, `50000 KRW`, 3분 차이, 같은 설명을 가진다. 한 거래는 `ESSENTIAL_EXPENSE`, 다른 규칙은 `INTERNAL_TRANSFER`로 판정한다.

둘 다 계산에서 제외하고 `reviewReasonCodes = ["POSSIBLE_DUPLICATE", "CLASSIFICATION_CONFLICT"]`, `dataState = NEEDS_REVIEW`로 둔다. 사용자 확인 전 기준선·스탯·목표 진행률을 갱신하지 않는다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s15",
  "dataState": "NEEDS_REVIEW",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "excludedTransactionCount": 2,
    "reviewReasonCodes": ["POSSIBLE_DUPLICATE", "CLASSIFICATION_CONFLICT"],
    "baselineUpdated": false,
    "goalProgressUpdated": false
  }
}
```

### S16. 레이드 단계 경계

| 전체 진행률 | 단계 | 단계 진행률 | 보스 HP | 전이 |
| ---: | ---: | ---: | ---: | --- |
| `0 bp` | 1 | `0 bp` | `10000 bp` | 대기 |
| `3299 bp` | 1 | `9997 bp` | `3 bp` | 대기 |
| `3300 bp` | 2 | `0 bp` | `10000 bp` | 1단계 클리어 기록 |
| `5000 bp` | 2 | `5152 bp` | `4848 bp` | 대기 |
| `6599 bp` | 2 | `9997 bp` | `3 bp` | 대기 |
| `6600 bp` | 3 | `0 bp` | `10000 bp` | 2단계 클리어 기록 |
| `10000 bp` | 3 | `10000 bp` | `0 bp` | 목표 완료 |

현재 진행률이 `5000 -> 4200 bp`로 하락하면 `currentProgressBps = 4200`, `highestProgressBps = 5000`, `unlockedStage = 2`다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s16",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "boundaryTransitionsVerified": [3300, 6600, 10000],
    "currentProgressBps": 4200,
    "highestProgressBps": 5000,
    "unlockedStage": 2,
    "bossRecoveryApplied": false
  }
}
```

### S17. 자동저축 이중 반영 방지

1. `AUTO_SAVE_SETTING_REVIEWED` 앱 증거로 퀘스트가 완료되고 새 XP `20`을 한 번 기록한다.
2. 이 시점의 저축성 순유입은 바뀌지 않으므로 물개 스탯과 목표 진행률은 그대로다.
3. 이틀 뒤 `154000 KRW` 외부 저축 입금이 MyData에 나타난다.
4. 입금은 물개 스탯과 활성 저축 목표 지표에 한 번 반영한다.
5. 같은 입금으로 XP를 추가하지 않고 설정 확인 XP도 다시 지급하지 않는다.

기대 멱등 키는 퀘스트에 `SHA-256(questId|AUTO_SAVE_SETTING_REVIEWED|eventId)`, 금융 거래에 `deduplicationKey`를 각각 사용한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_s17",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T08:55:00+09:00",
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "questXpAwarded": 20,
    "questXpEventCount": 1,
    "savingDepositAppliedCount": 1,
    "financialProgressChangedBeforeDeposit": false
  }
}
```

### S18. 투자 판단 게임화 차단

사용자가 위험성향 확인, 분산 학습, 쏠림 설명 확인을 모두 완료하면 토끼의 투자 판단력은 `10000 bp`가 될 수 있다. 그러나 해당 목표·퀘스트 결과는 다음과 같다.

세 증거는 넓은 `APP_EVENT` 같은 유형으로 축약하지 않고 실제 `verificationSource`를 `QUEST_EVIDENCE`에 각각 저장한다.

```json calculation-envelope
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "app-events-s18",
  "dataState": "FRESH",
  "lastSyncedAt": null,
  "calculatedAt": "2026-07-12T09:00:00+09:00",
  "result": {
    "goalTemplateId": "goal.investment.judgment-check.v1",
    "difficulty": "CHALLENGE",
    "verifiedCount": 3,
    "progressBps": 10000,
    "evidence": [
      {
        "verificationSource": "APP_RISK_PROFILE_EVENT",
        "sourceReferenceHash": "1111111111111111111111111111111111111111111111111111111111111111"
      },
      {
        "verificationSource": "APP_DIVERSIFICATION_LEARNING_EVENT",
        "sourceReferenceHash": "2222222222222222222222222222222222222222222222222222222222222222"
      },
      {
        "verificationSource": "APP_CONCENTRATION_EXPLANATION_EVENT",
        "sourceReferenceHash": "3333333333333333333333333333333333333333333333333333333333333333"
      }
    ],
    "xpAwarded": 0,
    "financialReward": "NONE",
    "celebrationEffect": false,
    "characterGrowth": false,
    "tradePromptCreated": false
  }
}
```

투자금 입금, 매수, 매도, 수익 발생 이벤트를 추가해도 위 필드와 퀘스트 완료 수는 변하지 않는다.

## 4. 승인 테스트 체크리스트

- `S01`의 후보와 목표 금액이 정수 산술로 정확히 재현된다.
- `S02`, `S08`에서 0 분모 계산이 발생하지 않는다.
- `S03`의 일회성 입금이 소득 중앙값에 들어가지 않는다.
- `S04`, `S17`에서 같은 금융행동이 두 번 합산되지 않는다.
- `S05`, `S06`, `S13`에서 원본 사용자와 정확 금융값이 응답에 나타나지 않는다.
- `S07`, `S15`에서 이전 진행률은 유지되며 새 정량 계산이 중지된다.
- `S09`, `S11`의 임계값 비교가 `>= 2000 bp`, `> P30D`와 정확히 일치한다.
- `S10`의 만료 비교는 `now < expiresAt`일 때만 확정을 허용한다.
- `S12`에서 AI 실패가 목표 선택을 막지 않는다.
- `S14`, `S16`에서 최고 진행률과 해금 단계가 감소하지 않는다.
- `S18`에서 투자 행동으로 XP·금전 보상·축하 효과가 발생하지 않는다.
