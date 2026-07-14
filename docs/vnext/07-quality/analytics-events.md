# FinMate MVP 분석 이벤트 명세

> 이 문서는 제품 효과를 주장하기 위한 결과표가 아니라, 합성데이터 MVP에서 핵심
> 퍼널과 안전 가드레일을 동일한 방식으로 측정하기 위한 계약이다.

## 1. 수집 원칙

- 이벤트 이름은 `snake_case`, 시각은 UTC ISO 8601을 사용한다.
- 클라이언트가 만든 `eventId`로 중복을 제거한다.
- 사용자 식별자는 내부 가명 ID만 사용한다. 이메일, 이름, 계좌번호, 정확 잔액,
  거래 원문, 상품·종목 보유내역은 이벤트 속성으로 전송하지 않는다.
- 금액이 필요한 제품 분석은 이벤트가 아니라 서버의 검증된 집계 뷰에서 계산한다.
- `route`, `appVersion`, `viewportWidth`, `dataState`, `calculationVersion`,
  `syntheticData=true`를 공통 속성으로 사용한다.
- 발표용 demo 이벤트는 `environment=demo`로 분리하며 운영 KPI에 합산하지 않는다.

## 2. 핵심 이벤트

| 이벤트 | 발생 시점 | 필수 속성 |
| --- | --- | --- |
| `signup_completed` | 가입 성공 | `onboardingStatus` |
| `onboarding_step_completed` | 각 단계 다음 이동 | `stepId`, `stepIndex` |
| `baseline_viewed` | 기준선 진단 표시 | `dataState`, `hasDisposableIncome` |
| `goal_confirmed` | 주 목표 확정 | `goalDomain`, `targetMonth`, `goalId` |
| `explore_mode_started` | 목표 없이 탐색 선택 | `source=baseline` |
| `raid_viewed` | 홈 레이드 표시 | `goalId`, `stage`, `raidStatus` |
| `character_report_viewed` | 동물 리포트 표시 | `reportType` |
| `quest_detail_viewed` | 퀘스트 상세 표시 | `questId`, `verificationKind`, `status` |
| `quest_accepted` | 수락 성공 | `questId`, `verificationKind` |
| `quest_completion_recorded` | 완료 명령 성공 | `questId`, `resultStatus`, `xpAwarded` |
| `mate_section_viewed` | 친구·메이트 찾기·비교 탐색 표시 | `section` |
| `mate_filters_submitted` | 검수 필터 검색 | `filterPresetId`, `resultCount` |
| `adventurer_report_viewed` | 익명 비교 리포트 표시 | `groupId`, `adventurerId` |
| `routine_recommendation_viewed` | 개인화 후보 표시 | `adaptationId`, `recommendedDifficulty` |
| `routine_applied` | 신규 적용·교체 성공 | `candidateId`, `difficulty`, `replacedExisting` |
| `product_information_viewed` | 검수 상품 정보 표시 | `productId`, `reviewedCatalog` |
| `official_product_link_opened` | 공식 정보 링크 이동 | `productId`, `informationAsOf` |
| `record_month_viewed` | 기록 월 표시 | `month`, `recordedDays` |
| `record_day_opened` | 날짜 바텀시트 표시 | `date`, `activityCount`, `dataState` |
| `reflection_saved` | 회고 저장 | `date`, `characterCountBucket` |

상품 정보 이벤트에는 `goalProgress`, XP, 포인트 또는 금융 스탯 변경 속성을 두지
않는다. 투자 퀘스트에는 금액, 수익률, 매수·매도 속성을 두지 않는다.

## 3. KPI 정의

| KPI | 분자 / 분모 | 관찰 창 |
| --- | --- | --- |
| 목표 확정률 | `goal_confirmed` 사용자 / `baseline_viewed` 사용자 | 같은 세션 또는 24시간 |
| 모험가→리포트 전환율 | `adventurer_report_viewed` / 익명 모험가 상세 조회 | 7일 |
| 리포트→루틴 적용률 | `routine_applied` / `adventurer_report_viewed` | 7일 |
| 첫 퀘스트 수락률 | 첫 `quest_accepted` / 목표 확정 사용자 | 7일 |
| 검증된 첫 행동 완료율 | 금융증거 확인 완료 사용자 / 목표 확정 사용자 | 14일 |
| D7 재방문율 | 7일째 활성 사용자 / 가입 사용자 | 가입일 기준 |
| 4주 루틴 유지율 | 28일째 동일 루틴 활성 사용자 / 루틴 적용 사용자 | 적용일 기준 |

`quest_completion_recorded`의 `DATA_PENDING`은 검증된 첫 행동 완료에 포함하지 않는다.
동기화된 금융데이터로 서버가 완료를 확정한 시점만 분자에 포함한다.

## 4. 가드레일

- 목표 또는 루틴 거절·교체율
- `STALE`·`INSUFFICIENT` 상태에서 막힌 세션 비율
- 데이터 불일치 신고 건수
- 비교 압박 사용자 응답
- 상품 가입 유도 오인 건수
- 투자 유도 문구·수익률 보상·정확 금융값 무동의 노출 `0건`
- 철회된 공개 프로필이 탐색·추천·캐시에 남은 건수 `0건`

## 5. 검수

이벤트 추가 PR은 화면 ID, 이벤트 이름, 스키마, 수집 목적, 보존 기간과 삭제 방법을
함께 제시해야 한다. 개발·staging에서 이벤트 payload를 캡처해 금지 필드가 없는지
확인한 뒤에만 production 전송을 허용한다.
