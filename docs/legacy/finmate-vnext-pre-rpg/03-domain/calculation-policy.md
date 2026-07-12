# FinMate vNext 결정적 계산 정책

## 1. 버전과 수치 규칙

이 문서의 규칙 버전은 `calculationVersion = "goal-calc-1.0.0"`이다. 계산 엔진은 같은 입력, 같은 `sourceDataVersion`, 같은 버전에 대해 바이트 단위로 같은 정수 결과를 내야 한다.

### 1.1 기본 단위

- 금액: 64비트 부호 있는 `KRW` 정수. 소수 원은 입력 단계에서 허용하지 않는다.
- 비율: `basis point` 정수. `1 bp = 0.01%`, `10000 bp = 100%`.
- 진행률·보스 HP·스탯: `0..10000 bp`.
- 시각: UTC 오프셋이 있는 ISO 8601. 계산 경계는 사용자 시간대 `Asia/Seoul`의 달력일을 UTC 시각으로 변환해 저장한다.
- 월 기준선: 시작일을 포함하고 종료일을 제외하는 완료월 반개구간을 사용한다. 현재 진행 중인 월은 제외한다.

입력과 영속 값은 signed 64-bit 범위지만 덧셈, 절댓값, 곱셈, 가중합, 중앙값의 두 가운데 값 합, 분자 확대에는 `arbitrary-precision` 정수 또는 그보다 넓고 오버플로가 증명 가능하게 탐지되는 정수 타입을 사용한다. 중간 계산을 binary floating point로 바꾸지 않는다. 최종 값을 저장하기 직전에 범위와 단위를 검증한 `checked int64` 변환을 수행하며 범위를 벗어나면 `NUMERIC_OVERFLOW`로 전체 계산을 실패시킨다. 포화, wraparound, 일부 값만 저장하는 처리는 금지한다.

### 1.2 정수 함수

모든 나눗셈의 분모는 양수여야 한다.

```text
clamp(x, low, high) = min(max(x, low), high)

roundHalfUpNonNegative(numerator, denominator)
  = floor((2 × numerator + denominator) ÷ (2 × denominator))

roundRationalToStep(numerator, denominator, step)
  = step × roundHalfUpNonNegative(numerator, denominator × step)

roundToStep(value, step)
  = roundRationalToStep(value, 1, step)

ratioBps(numerator, denominator)
  = roundHalfUpNonNegative(numerator × 10000, denominator)
```

음수 값을 반올림할 때는 절댓값에 `roundHalfUpNonNegative`를 적용한 뒤 부호를 복원한다. 목표값 반올림은 보간 후 한 번만 수행한다. 중간값은 반올림하지 않는다.

중앙값은 정렬한 정수열의 가운데 값이다. 개수가 짝수이면 가운데 두 값 합을 2로 `roundHalfUpNonNegative`한다.

## 2. 거래 정규화와 포함 규칙

### 2.1 중복 판정

계산 엔진이 소비하는 canonical transaction row와 중복 winner 계약은 다음 manifest다. ERD와 MyData adapter가 같은 manifest를 반복 선언하며 executable verifier가 byte-for-byte 구조 동등성을 확인한다.

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

`LP_UTF8_V1`의 `LP(value)`는 canonical scalar text의 UTF-8 byte 길이를 선행 0 없는 ASCII 십진수로 쓰고 `:`와 raw UTF-8 byte를 붙인다. null은 정확히 ASCII `-1:`이다. 정수는 부호가 필요한 경우에만 `-`를 쓰는 선행 0 없는 10진수, UUID는 lowercase hyphenated form, 시각은 같은 instant를 UTC `Z` RFC 3339로 정규화하고 불필요한 소수 초 후행 0을 제거한다.

```text
deduplicationKey = SHA-256(
  LP("finmate-transaction-dedup-v1") ||
  LP(field1) || ... || LP(fieldN)
)
```

안정적인 `providerTransactionId`가 있으면 manifest의 `withProviderTransactionIdFields`, 없으면 `withoutProviderTransactionIdFields`를 정확한 순서로 쓴다. separator 연결이나 JSON object key 순서에 의존하는 직렬화는 금지한다.

같은 키의 winner는 manifest 순서를 한 번만 적용한다. 즉 `providerUpdatedAt` 최신(null은 마지막), `sourceReceivedAt` 최신, `sourceResponseId` Unicode code point 사전순 최소, `sourceRecordOrdinal` 최소, `canonicalRecordSha256` lowercase hex 사전순 최소다. 배열 도착 순서, DB 물리 순서, 현재 서버 시각은 사용하지 않는다. 서로 다른 키라도 계좌·시각(`PT5M` 이내)·금액·방향·설명이 모두 같으면 `POSSIBLE_DUPLICATE`로 두고 확인 전 계산에서 제외한다.

### 2.2 분류 우선순위

아래 첫 일치 규칙을 적용한다.

| 우선순위 | 조건 | `classification` | 수입 | 지출 | 저축 성과 |
| --- | --- | --- | --- | --- | --- |
| 1 | 승인 취소·환불이 원거래와 연결됨 | `REVERSAL` | 제외 | 원거래에서 상계 | 제외 |
| 2 | 소유자 동일 계좌 간 양쪽 거래가 연결됨 | `INTERNAL_TRANSFER` | 제외 | 제외 | 제외 |
| 3 | 대출 실행 또는 원금 상환 | `LOAN_PRINCIPAL` | 제외 | 필수지출 원금 항목만 별도 집계 | 제외 |
| 4 | 예적금 만기와 재예치가 ISO `P3D` 달력일 안에 연결됨 | `MATURITY_REINVESTMENT` | 제외 | 제외 | 제외 |
| 5 | 증권 연결계좌와 증권계좌 간 단순 이동 | `BROKERAGE_TRANSFER` | 제외 | 제외 | 제외 |
| 6 | 급여·사업·용역 소득으로 확인됨 | `EARNED_INCOME` | 포함 | 제외 | 제외 |
| 7 | 월세·관리비·통신·정기교통·보험·부채상환 | `ESSENTIAL_EXPENSE` | 제외 | 포함 | 제외 |
| 8 | 예적금·지정 비상금 계좌로의 외부 순유입 | `SAVING_CONTRIBUTION` | 제외 | 제외 | 포함 |
| 9 | 일반 구매·생활비 | `DISCRETIONARY_EXPENSE` | 제외 | 포함 | 제외 |
| 10 | 분류 충돌 또는 근거 부족 | `REVIEW_REQUIRED` | 제외 | 제외 | 제외 |

사용자 수정은 자동 분류보다 우선한다. 수정 전·후 값과 사유를 append-only로 저장한다. `REVIEW_REQUIRED`와 가능한 중복이 목표 계산 기간에 하나라도 있으면 `dataState = NEEDS_REVIEW`다.

### 2.3 환불 상계

원거래와 환불이 같은 계산 기간이면 순지출은 `원거래 KRW - 환불 KRW`다. 환불이 다음 기간에 도착하면 환불 도착 기간의 동일 지출 카테고리에서 차감하되 기간 순지출 하한은 `0 KRW`다. 남는 환불액은 이전 기간 수정 스냅샷을 만들며 소득으로 계산하지 않는다.

## 3. 기준선

### 3.1 기준 월소득

```text
정규소득 기준 월소득 = 최근 3개 완료월 세후 근로·사업소득의 중앙값
불규칙소득 기준 월소득 = 최근 6개 완료월 세후 근로·사업소득의 중앙값
```

- 일회성 증여, 대출 실행, 보험금, 자산 매각, 내부이체는 제외한다.
- 정규소득은 3개 완료월 모두, 불규칙소득은 6개 중 최소 4개 완료월에 유효 소득이 있어야 한다.
- 최소 월 수를 충족하지 못하면 `dataState = INSUFFICIENT`이며 정량 후보를 만들지 않는다.

### 3.2 필수지출과 여윳돈

수용력 기준의 월 필수지출은 소득과 같은 각 완료월에서 다음 두 분류를 합한 뒤 그 월 합계의 중앙값을 취한다.

```text
monthlyEssentialExpenseKrw
  = sum(DEBIT ESSENTIAL_EXPENSE)
  + sum(DEBIT LOAN_PRINCIPAL)

baselineEssentialExpenseKrw
  = median(monthlyEssentialExpenseKrw for completed months)
```

`ESSENTIAL_EXPENSE`와 `LOAN_PRINCIPAL`은 상호 배타적인 canonical 분류다. 대출 원금 상환 거래는 `LOAN_PRINCIPAL`로 월 필수지출에 정확히 한 번 포함하며 `ESSENTIAL_EXPENSE`로 다시 더하지 않는다. 공급자가 분리한 대출 이자는 `ESSENTIAL_EXPENSE`로 포함한다. 대출 실행 `CREDIT`은 소득과 필수지출 모두에서 제외한다. `LOAN_PRINCIPAL`은 재량지출·소비절감 목표의 지출 성과에서는 제외하지만 수용력 기준에서는 사라지지 않는다.

```text
surplusKrw = baselineMonthlyIncomeKrw - baselineEssentialExpenseKrw
```

`surplusKrw <= 0`이면 소비율·저축률·투자율을 계산하지 않고 행동형 시작 목표만 제공한다.

**정상 예시:** 최근 정규소득이 `2700000, 2500000, 2600000 KRW`이면 중앙값은 `2600000 KRW`다. 같은 달 `ESSENTIAL_EXPENSE`가 `1280000, 1310000, 1300000 KRW`, `LOAN_PRINCIPAL` 원금 상환이 매월 `200000 KRW`이면 월 필수지출은 `1480000, 1510000, 1500000 KRW`다. 중앙값은 `1500000 KRW`, 여윳돈은 `1100000 KRW`다. 원금을 누락한 `1300000 KRW`와 두 번 더한 `1700000 KRW`는 모두 오답이다.

**경계 예시:** 소득 `1800000 KRW`, 필수지출 `1850000 KRW`이면 여윳돈은 `-50000 KRW`다. 비율 분모를 만들지 않으며 `예산 기록하기`, `고정비 확인하기`, `지원정보 살펴보기`만 제안한다.

### 3.3 기준선 변화 판정

기준값이 양수일 때:

```text
changeBps = ratioBps(abs(newValueKrw - baselineValueKrw), baselineValueKrw)
needsRecalibration = incomeChangeBps >= 2000 OR essentialExpenseChangeBps >= 2000
```

기준값이 `0`이고 새 값이 다르면 `needsRecalibration = true`다. 정확히 `2000 bp`도 재설정 대상이다.

**예시:** 고정한 소득 `2600000 KRW`가 `2080000 KRW`로 바뀌면 차이는 `520000 KRW`, `changeBps = 2000`이므로 목표값을 자동 수정하지 않고 `NEEDS_RECALIBRATION`로 전환한다.

## 4. 금융 지표

### 4.1 저축성 순유입과 저축률

```text
netSavingInflowKrw
  = validSavingDepositsKrw - validSavingWithdrawalsKrw

savingRateBps
  = clamp(ratioBps(max(netSavingInflowKrw, 0), surplusKrw), 0, 10000)
```

전제는 `surplusKrw > 0`이다. 만기 재예치, 내부이체, 증권계좌 이동은 양쪽 모두 제외한다. 순유입 원금은 `KRW`로 보존하고 비율만 `0..10000 bp`로 제한한다.

**예시:** 여윳돈 `1100000 KRW`, 유효 저축 입금 `170000 KRW`, 중도 인출 `16000 KRW`이면 순유입은 `154000 KRW`이고 저축률은 정확히 `1400 bp`다.

### 4.2 비상금 증가

```text
emergencyFundNetIncreaseKrw
  = endVerifiedBalanceKrw - baselineVerifiedBalanceKrw
```

지정 현금성 계좌만 포함한다. 신용한도, 증권 평가액, 만기 재예치 금액은 제외한다. 목표 진행용 현재값은 `baselineVerifiedBalanceKrw + emergencyFundNetIncreaseKrw`다.

### 4.3 예산 사용률과 범위 유지

검증 단위는 `Asia/Seoul` 달력일이다.

```text
dailyBudgetCapKrw = roundHalfUpNonNegative(periodBudgetKrw, eligibleDays)
daySpendKrw = max(validExpenseKrw - linkedRefundKrw, 0)
dayInRange = daySpendKrw <= dailyBudgetCapKrw
```

일별 한도보다 적게 쓴 경우도 `dayInRange = true` 하나만 얻으며 추가 성과는 없다. 데이터가 없는 날은 검증 단위에서 제외하지 않고 지출 `0 KRW`로 보되, 해당 기간 계좌 동기화가 성공했고 `dataState = FRESH`여야 한다.

### 4.4 반복결제 점검

같은 가맹점 정규화 키에서 `25..35일` 간격으로 최근 3회 이상 결제된 거래를 반복결제로 본다. 점검 이벤트는 `KEEP`, `CANCEL_REQUESTED`, `NOT_RECURRING` 중 하나를 사용자가 선택하고 저장했을 때 1회 완료된다. 실제 취소 여부나 지출 감소를 행동형 완료 조건으로 요구하지 않는다.

## 5. 네 동물 스탯

스탯은 목표 진행률·퀘스트 XP와 별도 계산한다.

### 5.1 곰: 소비 방어력

최근 30일 중 `FRESH` 금융 데이터로 검증된 날짜를 사용한다.

```text
spendingDefenseBps = ratioBps(inRangeDayCount, verifiedDayCount)
```

`verifiedDayCount < 7`이면 `dataState = INSUFFICIENT`다. 예산보다 적게 쓴 폭은 점수에 영향을 주지 않는다.

**예시:** 검증일 28일 중 22일이 범위 안이면 `ratioBps(22, 28) = 7857 bp`다.

### 5.2 물개: 저축 HP

```text
savingRateComponentBps
  = clamp(ratioBps(currentSavingRateBps, userSafeSavingRateTargetBps), 0, 10000)

emergencyFundComponentBps
  = clamp(ratioBps(emergencyFundBalanceKrw, essentialExpenseTargetKrw), 0, 10000)

autoSavingComponentBps
  = ratioBps(verifiedScheduledDeposits, expectedScheduledDeposits)

savingHpBps
  = roundHalfUpNonNegative(
      5000 × savingRateComponentBps
      + 3000 × emergencyFundComponentBps
      + 2000 × autoSavingComponentBps,
      10000)
```

`userSafeSavingRateTargetBps`는 확정된 템플릿 목표 또는 사용자 설정값이며 `500..5000 bp`다. `essentialExpenseTargetKrw`는 필수지출 1개월분이다. 예정 자동저축이 없으면 자동저축 구성요소를 제외하고 남은 가중치 `8000`으로 재정규화한다.

**예시:** 구성요소가 각각 `7000, 5000, 10000 bp`이면 `(5000×7000 + 3000×5000 + 2000×10000) / 10000 = 7000 bp`다.

### 5.3 토끼: 투자 판단력

최근 365일 안의 검증된 점검 이벤트만 사용한다.

```text
investmentJudgmentBps
  = 4000 × riskProfileChecked
  + 3000 × concentrationExplanationReviewed
  + 3000 × diversificationLearningCompleted
```

각 항은 참이면 `1`, 거짓이면 `0`이다. 투자금액·수익률·거래 횟수는 입력하지 않는다. 세 항목 모두 앱 이벤트이므로 앱 전용 결과는 검증 이벤트 시각을 `asOf`에 두고 `lastSyncedAt = null`로 명시한다. 금융 원천과 결합한 리포트의 `lastSyncedAt`은 필요한 범위가 모두 성공한 마지막 금융 sync 시각만 사용한다.

### 5.4 새: 퀘스트 XP

XP는 append-only 이벤트의 합이다.

| 검증 이벤트 | XP |
| --- | ---: |
| 금융 퀴즈 3문제 중 2문제 이상 정답 | 30 |
| 승인된 금융 리포트 끝까지 확인 | 20 |
| 일일 금융 기록 저장 | 10 |
| 주간 회고 저장 | 40 |
| 소비 거래 분류 3건 확인 | 20 |
| 자동저축 설정 확인 | 20 |
| 투자 판단 점검 | 0 |

같은 `evidenceId`는 한 번만 합산한다. XP 차감은 없다. 레벨은 `level = floor(totalXp / 200) + 1`이며 레벨은 금융 스탯이나 목표 진행률을 변경하지 않는다.

## 6. 목표 참고값과 후보

### 6.1 백분위와 참고값

백분위는 값과 `sourceProfileKey` 오름차순으로 정렬한 최근 검증 루틴 집합에 nearest-rank를 적용한다. 추천은 다음 결정적 코호트 순서를 먼저 적용한다.

1. 정확 코호트는 목표 영역, 소득 규칙성, 여윳돈·부채부담 등 금융 수용력 구간과 사용자가 선택한 비금융 생활맥락 태그를 모두 일치시킨다.
2. 정확 코호트의 적격 사용자가 `30`명 이상이면 공개 동의와 개별 안전 검사를 통과한 `INDIVIDUAL` 카드만 만들 수 있다.
3. 정확 코호트가 `30`명 미만이면 개인 카드를 만들지 않는다. `cohortDefinitionVersion`에 고정된 일반화 순서대로 주거 세부형태, 가구형태 같은 **비금융 생활맥락 태그만** 한 단계씩 제거한다. 소득 규칙성, 여윳돈 구간, 부채부담, 목표 안전범위 같은 금융 수용력 제약은 유지한다.
4. 처음으로 적격 사용자 `30`명 이상이며 재식별·이상치 검사를 통과한 안전한 상위 코호트를 찾으면 개인 이름·아바타·개인 유지기간이 없는 집계 `GROUP_ROUTINE` 카드 하나를 만든다. 카드에는 실제 `cohortSize >= 30`, 집계 루틴 구간, 검증일, 데이터 신선도를 넣는다.
5. 모든 허용 상위 코호트가 `30`명 미만이면 카드 배열을 비우고 `dataState = INSUFFICIENT`, `reasonCode = SAFE_COHORT_TOO_SMALL`을 반환한다. `30`명 미만 집계와 금융 수용력 제약 완화는 어떤 경우에도 금지한다.

응답의 `fallbackCardKind = GROUP_ROUTINE`은 그룹 fallback을 평가했다는 분기 메타데이터일 뿐 카드 생성 증거가 아니다. 실제 생성 여부는 `items`와 `recommendationState`로 판단하며 안전 코호트가 30명 미만이면 `items = []`다.

```text
rank(p) = ceil(p × N)
percentile(p) = sortedValues[rank(p)]  // 1부터 시작
```

백분위와 루틴 구간을 계산하는 코호트의 `N < 30`이면 카드를 추천하지 않는다. 출처 지표가 `P10..P90` 밖이면 개인 카드를 추천하지 않는다. `GROUP_ROUTINE`은 개별 출처 값을 사용하지 않고 안전 코호트의 승인된 집계 구간만 사용한다.

```text
groupLimited = clamp(sourceRoutineValue, P25, P75)
referenceValue = clamp(groupLimited, safeMinimum, safeMaximum)
```

### 6.2 증가형 후보

난이도 계수는 `LIGHT=2500`, `STANDARD=5000`, `CHALLENGE=7500 bp`다.

```text
rawTargetNumerator
  = baseline × 10000 + (referenceValue - baseline) × alphaBps
clampedTargetNumerator
  = clamp(rawTargetNumerator, safeMinimum × 10000, safeMaximum × 10000)
target
  = roundRationalToStep(clampedTargetNumerator, 10000, roundingStep)
```

곱셈과 나눗셈은 위 분자로 유지하고 마지막 `roundRationalToStep`에서만 반올림한다. `referenceValue <= baseline` 또는 반올림 후 `target == baseline`이면 해당 후보를 폐기한다.

**정상 예시:** 기준 저축률 `800 bp`, 참고값 `2000 bp`, 반올림 단위 `100 bp`다.

| 난이도 | 계산 | 목표 |
| --- | --- | ---: |
| `LIGHT` | `800 + (2000-800)×2500/10000` | `1100 bp` |
| `STANDARD` | `800 + (2000-800)×5000/10000` | `1400 bp` |
| `CHALLENGE` | `800 + (2000-800)×7500/10000` | `1700 bp` |

저축률 목표 금액은 다음처럼 계산한다.

```text
targetAmountKrw
  = roundRationalToStep(surplusKrw × targetSavingRateBps, 10000, 1000)
```

표준 목표 금액은 `roundRationalToStep(1100000 × 1400, 10000, 1000) = 154000 KRW`다.

### 6.3 감소형 후보

```text
rawTargetNumerator
  = baseline × 10000 - (baseline - referenceValue) × alphaBps
clampedTargetNumerator
  = clamp(rawTargetNumerator, safeMinimum × 10000, safeMaximum × 10000)
target
  = roundRationalToStep(clampedTargetNumerator, 10000, roundingStep)
```

`referenceValue >= baseline` 또는 반올림 후 기준과 같으면 폐기한다. MVP 카탈로그에는 감소형 정량 목표가 없으며, 이 식은 승인된 후속 템플릿에만 사용할 수 있다.

### 6.4 범위 유지형과 행동형

범위 유지형과 행동형은 보간하지 않는다. 카탈로그에 승인된 `requiredCount`, 기간, 범위만 사용한다.

```text
rangeProgressBps = clamp(ratioBps(inRangeVerifiedCount, requiredCount), 0, 10000)
actionProgressBps = clamp(ratioBps(verifiedCount, requiredCount), 0, 10000)
```

**범위 예시:** 예산 범위 STANDARD 후보의 `requiredCount = 5`다. 검증된 범위 내 날짜가 5일이면 `5 / 5 = 10000 bp`, 4일이면 `ratioBps(4,5) = 8000 bp`다. 나머지 기간의 초과일은 분모를 7로 바꾸지 않으며 매우 적게 쓴 날도 1일로만 센다.

**행동 예시:** 금융 퀴즈 3개 중 2개를 검증하면 `ratioBps(2,3) = 6667 bp`다.

## 7. 목표 진행률

### 7.1 증가형

```text
progressBps
  = clamp(
      ratioBps(max(currentValue - baselineValue, 0), targetValue - baselineValue),
      0,
      10000)
```

`targetValue > baselineValue`여야 한다.

**예시:** 기준 `800 bp`, 목표 `1400 bp`, 현재 `1100 bp`이면 `(1100-800)/(1400-800) = 5000 bp`다. 현재가 `700 bp`면 `0 bp`, `1600 bp`면 `10000 bp`다.

### 7.2 감소형

```text
progressBps
  = clamp(
      ratioBps(max(baselineValue - currentValue, 0), baselineValue - targetValue),
      0,
      10000)
```

`baselineValue > targetValue`여야 한다.

### 7.3 기준과 목표가 같은 경우

목표 후보 생성 시 `targetValue == baselineValue`면 후보를 저장하지 않는다. 이미 저장된 데이터가 손상돼 분모가 `0`이면 계산을 실패 처리하고 `dataState = NEEDS_REVIEW`, `changeReason = INVALID_TARGET_DENOMINATOR` 스냅샷을 남긴다. 임의로 `0%` 또는 `100%`를 반환하지 않는다.

## 8. 레이드 계산

목표 진행률 `p`는 `0..10000 bp`다.

```text
raidStage = 1, if p < 3300
raidStage = 2, if 3300 <= p < 6600
raidStage = 3, if 6600 <= p <= 10000

stage1ProgressBps = clamp(roundHalfUpNonNegative(p × 10000, 3300), 0, 10000)
stage2ProgressBps = clamp(roundHalfUpNonNegative((p - 3300) × 10000, 3300), 0, 10000)
stage3ProgressBps = clamp(roundHalfUpNonNegative((p - 6600) × 10000, 3400), 0, 10000)

bossHpBps = 10000 - currentStageProgressBps
highestProgressBps = max(previousHighestProgressBps, p)
unlockedStage = max(previousUnlockedStage, raidStage)
```

정확히 `3300 bp`이면 1단계를 클리어하고 2단계 `0 bp`, 정확히 `6600 bp`이면 2단계를 클리어하고 3단계 `0 bp`다. `10000 bp`에서 3단계 진행률 `10000 bp`, 보스 HP `0 bp`다.

**예시:** 전체 진행률 `5000 bp`이면 2단계이며 단계 진행률은 `round(1700/3300×10000) = 5152 bp`, 보스 HP는 `4848 bp`다. 이후 현재 진행률이 `4200 bp`로 내려가도 최고 진행률 `5000 bp`와 해금 단계 2는 유지한다.

## 9. 데이터 상태와 계산 게이트

```text
dueAt = lastSyncedAt + consentedSyncInterval
staleAt = dueAt + P2D
```

판정 순서:

1. 계산 기간에 검토 필요 거래가 있으면 `NEEDS_REVIEW`.
2. 템플릿 최소 데이터가 부족하면 `INSUFFICIENT`.
3. `now >= staleAt`이면 `STALE`.
4. 검증 대기 사용자 행동이 있고 원천 미도착이면 `PENDING`.
5. 나머지는 `FRESH`.

`lastSyncedAt`가 없으면 `INSUFFICIENT`다. `FRESH`에서만 새 정량 후보를 확정하고 목표 진행률을 갱신한다. `PENDING`은 이전 값을 유지하며 대기만 표시한다. `STALE`, `INSUFFICIENT`, `NEEDS_REVIEW`는 진행 갱신을 중지한다.

**시각 예시:** `lastSyncedAt = 2026-07-01T09:00:00+09:00`, 동의 주기 `P7D`이면 `dueAt = 2026-07-08T09:00:00+09:00`, `staleAt = 2026-07-10T09:00:00+09:00`이다. 그 시각부터 새 성공 동기화 전까지 `STALE`다.

## 10. 내부 계산 결과 봉투

다음 전체 봉투는 계산 엔진 경계, 골든 테스트, 합성 시나리오와 계산·스냅샷 영속화가 공유하는 **내부 계약**이다. 공개 HTTP 응답의 공통 wrapper가 아니다.

```json
{
  "calculationVersion": "goal-calc-1.0.0",
  "sourceDataVersion": "sdv_20260712_090000_7f3a",
  "dataState": "FRESH",
  "lastSyncedAt": "2026-07-12T09:00:00+09:00",
  "calculatedAt": "2026-07-12T09:00:01+09:00",
  "result": {}
}
```

내부 봉투의 field 순서는 `calculationVersion`, `sourceDataVersion`, `dataState`, `lastSyncedAt`, `calculatedAt`, `result`다. `result`는 계산별 내부 payload object이며 계산 field를 내부 봉투 바깥에 두지 않는다. `calculatedAt`는 계산 실행 시각이고 `lastSyncedAt`는 결과에 사용한 마지막 **완전 `SUCCEEDED`** 금융 원천 동기화 시각이다. 부분 성공과 서버 수신 시각은 이를 전진시키지 않으며 세 값을 서로 대체하지 않는다.

공개 API는 OpenAPI에 선언된 endpoint별 domain payload를 그대로 반환한다. 각 payload는 스키마가 요구하는 `dataFreshness`와 `calculationVersion` 또는 계산 메타데이터를 해당 위치에 포함하며, 공통 최상위 `result` wrapper를 추가하지 않는다. 내부 봉투에서 공개 payload로 투영할 때도 수치나 시각을 변환하지 않고 OpenAPI field에 명시적으로 매핑한다.
