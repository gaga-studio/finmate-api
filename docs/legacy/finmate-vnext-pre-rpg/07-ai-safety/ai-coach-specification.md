# AI 코치 명세

## 1. 목적과 비목적

FinMate의 AI 코치는 금융 의사결정자가 아니라 **서버가 허용한 목표 후보 ID 안에서 추천 후보를 순위 1위로 고르고 허용 근거를 쉬운 문장으로 설명하는 표현 계층**이다. 후보 ID와 목표값·범위, 기간, 진행률, 추천 카드 자격, 퀘스트 완료 여부와 레이드 상태는 결정론적 코드만 생성·계산한다. 사용자는 AI 추천을 따르지 않고 다른 후보를 선택하거나 목표 설정을 중단할 수 있다.

MVP에서 AI가 하는 일은 다음 두 가지뿐이다.

- 허용된 목표 후보 ID 중 하나를 추천하고 근거 코드를 설명한다.
- 가볍게·표준·도전 후보의 차이와 데이터 상태를 설명한다.

AI는 다음 일을 하지 않는다.

- 목표값·범위·기간·진행률·스탯·보상을 생성하거나 수정
- 리포트·회고·퀘스트·보스 문구 생성 또는 변형
- 추천 모험가의 자격, 유사그룹, 표본 수, 공개 범위 또는 데이터 품질 판정
- 상품·종목·매매·투자금 증액 추천, 수익률 전망 또는 금융상품 가입 유도
- 사용자 대신 목표 확정·일시정지·취소, 동의, 공개 설정 변경
- 원문 거래, 정확 소득·잔액, 상품명·종목명, 연락처, 위치 등 비허용 개인정보 사용
- 친구·팔로잉·메이트 찾기 기능 수행: 두 기능은 Phase 2 범위다.
- 현금성 또는 비현금성 경제 보상 생성·제안

## 2. 책임 경계

| 단계 | 결정론적 코드 | AI | 사용자 |
| --- | --- | --- | --- |
| 입력 준비 | 허용 필드만 선택, 값 구간화, ID 발급 | 입력을 추가 조회하지 않음 | 공개·마이데이터 동의 관리 |
| 카드 추천 | 공개·표본 30명·품질·유사성·안전 범위 판정과 유사 이유 생성 | 관여하지 않음 | 카드와 루틴 선택 |
| 후보 생성 | 후보 ID와 LIGHT·STANDARD·CHALLENGE 수치·기간·근거 계산 | 서버 허용 후보 중 추천 ID 선택과 허용 근거 설명 | 후보 확인·최종 확정 |
| 목표 실행 | 진행률·데이터 상태·레이드 상태와 설명 계산 | 관여하지 않음 | 일시정지·재개·취소 |
| 투자 영역 | 학습·점검 행동만 허용, 보상 차단 | 교육적 설명만 제공하며 허용된 행동형 후보의 차이만 다룸 | 학습·점검 여부 선택 |
| 실패 처리 | 기본 순서와 코드 근거 표시 | 호출 중단 | AI 없이 전체 흐름 이용 |

AI가 반환한 `recommendedCandidateId`는 서버 허용 후보 중 순위 1위만 제안한다. 나머지 후보의 순서는 코드의 `STANDARD → LIGHT → CHALLENGE → candidateId 오름차순` 규칙을 따르며, 서버가 생성한 후보 객체와 값은 절대 덮어쓰지 않는다.

## 3. 입력 계약

AI 게이트웨이는 아래 구조만 모델에 전달한다. 자유 형식 사용자 입력, 거래 적요, 카드 원문, 내부 사용자 키는 전달하지 않는다.

```json
{
  "requestId": "ai_01JZ8W2P7A",
  "task": "RANK_AND_EXPLAIN_GOAL_CANDIDATES",
  "locale": "ko-KR",
  "selectedCard": {
    "cardId": "card_7F3K",
    "routineId": "routine_save_first_30d",
    "similarityReasonCodes": ["INCOME_REGULARITY_MATCH", "HOUSING_COST_BAND_MATCH"],
    "reasonEvidence": [
      {
        "reasonCode": "INCOME_REGULARITY_MATCH",
        "approvedSummaryClaim": "소득 규칙성 구간이 비슷해요."
      },
      {
        "reasonCode": "HOUSING_COST_BAND_MATCH",
        "approvedSummaryClaim": "주거비 부담 구간이 비슷해요."
      }
    ],
    "maintainedForDays": 182,
    "publicMetricBand": "15-20%",
    "dataAsOf": "2026-07-10"
  },
  "userContext": {
    "goalDomain": "SAVING",
    "pacePreference": "BALANCED",
    "dataState": "FRESH",
    "baselineBand": "5-10%"
  },
  "candidates": [
    {
      "candidateId": "cand_light",
      "difficulty": "LIGHT",
      "targetDisplay": "11%",
      "durationDays": 30,
      "reasonCodes": ["LOW_CHANGE", "WITHIN_SAFE_RANGE"],
      "reasonEvidence": [
        {"reasonCode": "LOW_CHANGE", "approvedSummaryClaim": "변화 폭이 가장 작아요."},
        {"reasonCode": "WITHIN_SAFE_RANGE", "approvedSummaryClaim": "승인된 안전 범위 안에 있어요."}
      ]
    },
    {
      "candidateId": "cand_standard",
      "difficulty": "STANDARD",
      "targetDisplay": "14%",
      "durationDays": 30,
      "reasonCodes": ["PACE_MATCH", "WITHIN_SAFE_RANGE"],
      "reasonEvidence": [
        {"reasonCode": "PACE_MATCH", "approvedSummaryClaim": "선택한 변화 속도와 맞아요."},
        {"reasonCode": "WITHIN_SAFE_RANGE", "approvedSummaryClaim": "승인된 안전 범위 안에 있어요."}
      ]
    },
    {
      "candidateId": "cand_challenge",
      "difficulty": "CHALLENGE",
      "targetDisplay": "17%",
      "durationDays": 30,
      "reasonCodes": ["HIGH_CHANGE", "WITHIN_SAFE_RANGE"],
      "reasonEvidence": [
        {"reasonCode": "HIGH_CHANGE", "approvedSummaryClaim": "변화 폭이 가장 커요."},
        {"reasonCode": "WITHIN_SAFE_RANGE", "approvedSummaryClaim": "승인된 안전 범위 안에 있어요."}
      ]
    }
  ],
  "policy": {
    "schemaVersion": "1.0.0",
    "promptVersion": "goal-rank-ko-1.0.0",
    "allowedCandidateIds": ["cand_light", "cand_standard", "cand_challenge"]
  }
}
```

`targetDisplay`는 서버가 만든 표시 문자열이며 모델이 계산에 사용하지 않는다. `reasonEvidence`는 결정론적 카드 적격성·후보 계산기가 만든 코드와 승인 문장 조각이며 모델이 추가하거나 수정할 수 없다. 운영 로그에는 모델 입력 전체 대신 요청 ID, 허용 ID, 근거 코드, 정책 버전과 값의 해시를 기본 저장한다. 원문 입력 보관은 AI 동의가 유효한 동안 확인된 사고 조사에 필요한 최소 범위·기간·접근 권한을 별도 승인한 경우에만 허용하며, 철회·삭제 시 아래 보존 계약을 그대로 적용한다.

## 4. 구조화 출력 계약

응답은 JSON 전용 모드로 받고 추가 속성을 금지한다.

```yaml
$schema: https://json-schema.org/draft/2020-12/schema
title: FinMateAiCoachResult
type: object
additionalProperties: false
required:
  - schemaVersion
  - recommendedCandidateId
  - reasonCodes
  - summary
  - riskNotice
properties:
  schemaVersion:
    const: 1.0.0
  recommendedCandidateId:
    type: string
    minLength: 1
    maxLength: 64
  reasonCodes:
    type: array
    minItems: 1
    maxItems: 4
    uniqueItems: true
    items:
      enum:
        - PACE_MATCH
        - LOW_CHANGE
        - HIGH_CHANGE
        - WITHIN_SAFE_RANGE
        - ROUTINE_MAINTAINED
        - DATA_LIMITATION
        - INCOME_REGULARITY_MATCH
        - HOUSING_COST_BAND_MATCH
  summary:
    type: string
    minLength: 1
    maxLength: 240
  riskNotice:
    type: string
    minLength: 1
    maxLength: 160
```

출력 검증은 다음 순서로 수행한다.

1. JSON 파싱, 스키마 버전, 필수 필드, 길이, 열거형, 추가 필드 금지를 검사한다.
2. `recommendedCandidateId`가 요청의 `allowedCandidateIds`에 있는지 검사한다.
3. 선택된 후보의 `reasonEvidence`와 `selectedCard.reasonEvidence`의 합집합을 허용 근거 집합으로 만들고, 출력 `reasonCodes`가 그 부분집합인지 검사한다. 출력에는 선택된 후보 소유 근거가 최소 1개 있어야 하며 다른 후보의 근거를 가져올 수 없다.
4. `summary`의 각 사실 주장이 출력 `reasonCodes`가 가리키는 `approvedSummaryClaim` 중 하나와 일치하는지 검사한다. 승인된 접속어·선택권 안내 외의 문장을 추가하거나, 근거에는 없는 인과·비교·개인 속성을 추론하면 실패다.
5. 응답 문자열에 입력에 없던 금액·기간·비율·진행률이 추가되거나 입력값과 달라지지 않았는지 검사한다.
6. 상품·종목·매수·매도·대출 권유, 확정 수익 표현, 비교 압박, 수치심, 긴급성, 공개 순위 표현을 차단한다.
7. 요약이 선택권, 데이터 기준일, AI 보조 성격을 왜곡하지 않는지 정책 규칙으로 검사한다.
8. 통과한 출력만 렌더러에 전달하고, UI는 서버 후보 객체에서 수치와 기간을 별도로 표시한다.

검증기는 AI가 작성한 문장에서 숫자나 근거 없는 문장만 제거하는 방식으로 복구하지 않는다. 허용 근거 밖 코드는 `REASON_CODE_NOT_IN_EVIDENCE`, 승인 문장으로 입증할 수 없는 요약은 `SUMMARY_NOT_GROUNDED`로 기록한다. 하나라도 실패하면 전체 AI 출력을 폐기하고 결정론적 폴백을 제공하며 AI 문장은 노출하지 않는다.

### 4.1 공개 API 순위 투영

`POST /api/v1/goal-candidate-sets`와 `GET /api/v1/goal-candidate-sets/{candidateSetId}`의 `GoalCandidateSet`은 아래 `recommendation` 객체를 required로 제공한다. AI 성공과 폴백 모두 다섯 필드를 생략하거나 null로 만들 수 없다.

```json
{
  "recommendation": {
    "recommendedCandidateId": "70000000-0000-4000-8000-000000000002",
    "reasonCodes": ["PACE_MATCH", "WITHIN_SAFE_RANGE"],
    "summary": "선택한 변화 속도와 맞고 승인된 안전 범위 안에 있어요.",
    "riskNotice": "데이터 상태와 기준일을 확인한 뒤 직접 선택하세요.",
    "recommendationState": "AI_GENERATED"
  }
}
```

- `recommendedCandidateId`는 같은 응답의 `candidates[].candidateId` 중 하나여야 하며 순위 1위를 뜻한다.
- `reasonCodes`, `summary`, `riskNotice`는 4절 검증을 통과한 값 또는 5절의 승인된 결정론적 값만 사용한다.
- `recommendationState`는 서버가 설정하는 `AI_GENERATED | DETERMINISTIC_FALLBACK` 상태다. 모델 출력으로 받거나 AI 문장 유무로 추론하지 않는다.
- `candidates`의 ID, target, duration, verification source와 모든 숫자는 코드 소유이며 `recommendation`을 적용할 때 복사·재계산·덮어쓰기하지 않는다.
- 검증된 AI 출력만 `recommendationState = AI_GENERATED`다. timeout, 제공자 오류, 파싱·스키마·근거·수치·정책 검증 실패와 AI 동의 철회는 모두 `recommendationState = DETERMINISTIC_FALLBACK`이다.

## 5. 결정론적 폴백

폴백은 타임아웃, 제공자 오류, 파싱 오류, 스키마 오류, 허용되지 않은 ID, 수치 불일치, 안전 정책 위반, 회로 차단기 개방 시 동일하게 적용한다.

```text
후보 순위: difficulty 우선순위 STANDARD → LIGHT → CHALLENGE, 같은 difficulty면 candidateId 오름차순
recommendedCandidateId: 위 순위의 첫 후보 ID
reasonCodes: 추천 후보가 소유한 허용 근거 코드 중 서버 고정 순서의 최대 4개
summary: 선택된 reasonCodes의 approvedSummaryClaim만 승인된 접속어로 결합
riskNotice: 데이터 상태와 기준일을 확인하세요. 선택하지 않고 나가도 금융상태는 바뀌지 않아요.
recommendationState: DETERMINISTIC_FALLBACK
```

- 폴백은 모델 호출 없이 생성하고 지역화된 승인 문구 저장소에서 불러온다.
- AI 생성 표시는 숨기되 `recommendation.recommendationState = DETERMINISTIC_FALLBACK`의 승인된 순위·근거·요약·위험 안내와 후보 비교, 확정, 거절, 취소 기능은 항상 유지한다.
- 모델 호출 제한은 2.5초, 재시도는 일시 오류에 한해 1회이며 전체 사용자 대기 시간은 3초를 넘기지 않는다.
- 5분 이동 창에서 `검증 실패 응답 / 전체 모델 응답`이 2%를 **초과**하거나 10분 이동 창에서 `제공자 오류 요청 / 전체 제공자 요청`이 5%를 **초과**하면 회로 차단기를 열고 전량 폴백한다. 정확히 2%·5%일 때는 열지 않으며 다음 요청마다 다시 계산한다.
- 안전 위반 한 건은 즉시 해당 프롬프트·모델 조합을 중지하고 AI 책임자와 개인정보·보안 담당자에게 통보한다.

## 6. 프롬프트와 모델 운영

- 시스템 프롬프트와 출력 스키마는 코드 리뷰, 안전 리뷰, 제품 책임자 승인 후 버전 태그를 부여한다.
- 모델 버전은 허용 목록으로 고정하며 자동 업그레이드를 금지한다.
- MVP 프롬프트 변경은 합성 골든 테스트 전수 통과와 계약 어댑터 기반 오프라인 비교 평가로 승인한다. 실제 사용자 트래픽 관찰과 단계 확대는 실제 마이데이터 연동 이후의 post-MVP 검증 제안이며 MVP 인수 증거가 아니다.
- 평가셋은 정상, 데이터 부족, 오래된 데이터, 여윳돈 0 이하, 투자 행동형, 악성 문자열, 잘못된 후보 ID를 포함한다.
- 모델 학습·개선을 위한 사용자 데이터 재사용은 별도 목적과 적법 근거를 검토하기 전 금지한다.
- 프롬프트에는 후보 데이터가 명령이 아닌 데이터임을 명시하고, 카드·태그·요약 안의 지시문을 따르지 않도록 한다.

## 7. 감사와 인적 개입

`ai_audit_logs`에는 다음을 연결한다.

| 필드 | 보관 목적 |
| --- | --- |
| `requestId`, `userPseudonym` | 사고 추적과 사용자 문의 대응 |
| `modelProvider`, `modelVersion` | 재현과 공급자 변경 추적 |
| `promptVersion`, `schemaVersion`, `policyVersion` | 규칙 재현 |
| `candidateIds`, `reasonCodes`, `inputHash` | 허용 입력 입증 |
| `rawOutputHash`, `validatedOutput` | 검증 결과 입증 |
| `validationResult`, `fallbackReason` | 실패 유형과 폴백 측정 |
| `exposedText`, `exposedAt` | 실제 노출 내용 확인 |
| `userDecision` | 선택·수정·거절 결과 분석 |
| `incidentId` | 오류 신고와 조치 연결 |

원문 금융데이터와 내부 원본 사용자 키는 감사 로그에 저장하지 않는다. 운영자는 역할 기반 권한과 사유 기록을 거쳐서만 로그를 조회하며, 대량 내보내기를 금지한다. 사용자가 AI 설명 오류를 신고하면 고객지원이 노출 문장과 서버 계산을 분리해 확인하고, 금융 수치는 결정론적 스냅샷을 기준으로 답한다.

### 감사 로그 보존과 삭제

| 레코드 분류 | 보관·삭제 규칙 |
| --- | --- |
| `OPERATIONAL` | AI 동의가 유효한 일반 운영 로그도 `createdAt`부터 최대 90일이며 더 짧게 설정할 수 있다. `validatedOutput`, `exposedText`, `userDecision`은 같은 기한을 넘기지 않고 원문 모델 입력·출력은 기본 저장하지 않는다. |
| `INCIDENT_EVIDENCE` | 유효한 사고 티켓에 필요한 최소 필드만 별도 증거 저장소로 이동한다. 생성 시 `retentionExpiresAt`을 필수로 두고 최대 180일을 넘길 수 없으며, 티켓이 닫히거나 필요성이 끝나면 더 일찍 삭제한다. 자동 연장하지 않고 `AI_EXPLANATION` 철회·계정 삭제를 넘겨 존속할 수 없다. |
| `LEGAL_REQUIRED` | 법무가 구체적 법적 근거를 확인한 최소 기록만 분류한다. `legalBasisCitation`, `retentionExpiresAt`, `recordScope`, `approvedBy`, `legalCaseId`, `legalApprovedAt` 중 하나라도 없으면 이 분류로 저장할 수 없다. 만료 시 자동 삭제한다. |

- `AI_EXPLANATION` 철회는 새 호출과 대기 중 호출을 즉시 중지한다. 7일 이내 `OPERATIONAL`과 `INCIDENT_EVIDENCE`를 포함한 모든 non-`LEGAL_REQUIRED` 연결 레코드·콘텐츠·ID·해시·사용자-가명 매핑·재연결 키를 삭제하거나 되돌릴 수 없게 분리한다. `INCIDENT_EVIDENCE`는 비식별화만으로 존속할 수 없다.
- 계정 삭제는 같은 7일 규칙을 AI 감사 로그 전체에 적용한다. 단순히 사고 티켓이 열려 있거나 180일 상한이 남아 있다는 이유로 `INCIDENT_EVIDENCE`를 예외로 둘 수 없다.
- 철회 또는 삭제 요청 전에 여섯 필수 필드가 모두 기록되고 `legalApprovedAt`이 요청 시각보다 앞선 `LEGAL_REQUIRED` 최소 레코드만 각 만료일까지 격리 보관한다. 사건 뒤 사후 재분류나 누락 필드 보충은 예외를 만들지 않는다.
- 가명 연결 해제는 ID 매핑 레코드와 재연결 키를 삭제하거나 폐기해 되돌릴 수 없게 한다. 보존이 승인된 법적 기록에는 서비스 가명 대신 법적 저장소 전용 사건 가명을 사용하고 매핑 접근을 법무 승인 역할로 제한한다.
- 삭제 tombstone 원장은 `requestId` 원문 대신 삭제 작업 ID, 대상 범주, 처리 결과, 완료 시각만 남긴다. 백업은 최대 30일 내 만료하며 복원 시 철회·삭제 tombstone을 서비스 개방 전에 재생한다. 대상 레코드나 재연결 가능한 매핑이 한 건이라도 복원되면 복원을 실패 처리한다.
- 동의 철회·계정 삭제·법정 보존 예외의 실행 결과는 월별 표본 점검과 복원 리허설로 검증한다.

릴리스 중지 조건은 허용되지 않은 후보 ID 또는 변조 수치 노출 1건, 투자 권유 문구 1건, 비허용 개인정보 노출 1건이다. 중지 후에는 폴백만 제공하며 원인 제거, 영향 사용자 식별, 재발 방지 테스트 추가, 책임자 승인을 거쳐 재개한다.

## 8. 품질 지표

MVP에서는 계약 호환 어댑터와 합성 평가셋으로 아래 계산과 차단 조건을 검증한다. 실제 사용자 노출률과 응답 지표는 post-MVP 관찰값이며 MVP 완료 증거로 사용하지 않는다.

| 지표 | 계산 | 기준 |
| --- | --- | --- |
| 구조화 출력 유효율 | 스키마 통과 응답 / 모델 응답 | 99.5% 이상 |
| 근거 충실도 | 선택 카드·후보의 공급 근거 부분집합이며 모든 요약 주장이 승인 근거로 입증된 응답 / 검수 응답 | 99% 이상 |
| 잘못된 ID·수치 노출 | 실제 UI 노출 건수 | 0건 |
| 안전 위반 노출 | 투자 권유·압박·개인정보 노출 건수 | 0건 |
| 폴백 성공률 | 폴백에서 후보 선택 완료 가능 요청 / 폴백 요청 | 100% |
| AI P95 응답 시간 | 게이트웨이 요청부터 검증 종료 | 2.5초 이하 |

## 9. 준거

금융위원회의 [2026 금융분야 인공지능 가이드라인](https://www.fsc.go.kr/no010101/87142)이 제시한 거버넌스·합법성·보조수단성·신뢰성·금융안정성·신의성실·보안성을 운영 원칙으로 적용한다. 특히 최종 의사결정과 책임을 사람에게 두라는 보조수단성에 따라 목표 확정은 항상 사용자에게 남기고, 소비자 이익 우선 원칙에 따라 AI 설명보다 결정론적 안전 규칙과 폴백을 우선한다.
