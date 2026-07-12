# 이벤트 택소노미와 KPI

## 1. 측정 원칙

분석은 `추천 카드 노출 → 성장 방식 보기 → 루틴 선택 → 목표 후보 확인 → 목표 확정 → 연결 퀘스트 시작 → 검증된 첫 행동 → 레이드 1단계 도달`의 가치 전달을 측정한다. 앱 체류시간, 전투 반복, 투자 거래, 공개 경쟁을 성공 지표로 삼지 않는다.

- 모든 비율은 사용자 단위로 계산하고 같은 이벤트 재전송은 `event_id`로 제거한다.
- 클라이언트는 의도와 화면 노출을, 서버는 확정·검증·계산·동의 처리를 기록한다. KPI의 최종 상태는 서버 이벤트를 우선한다.
- 이벤트 시간은 UTC ISO 8601로 저장하고 제품 보고서는 `Asia/Seoul` 기준일로 집계한다.
- 이벤트 이름과 필드는 버전 관리하며 의미 변경은 새 이름 또는 `schema_version` 주 버전 변경으로 처리한다.
- 정확 금액, 거래 원문, 계좌·상품·종목, 연락처, 원본 카드 사용자 키를 이벤트에 넣지 않는다.
- 친구·팔로잉과 메이트 찾기는 Phase 2이며 MVP 이벤트와 KPI에 포함하지 않는다.
- 3~4주 MVP의 계측 인수는 계약 호환 어댑터가 만든 합성 이벤트와 고정 시계 KPI 변환 결과로만 판정한다. 실제 마이데이터와 실제 사용자 KPI는 post-MVP 검증 제안이며 MVP 인수 증거가 아니다.

## 2. 공통 이벤트 계약

```json
{
  "event_id": "evt_01JZ9F5Y3X",
  "event_name": "goal_confirmed",
  "schema_version": "1.0.0",
  "occurred_at": "2026-07-12T05:20:31Z",
  "received_at": "2026-07-12T05:20:32Z",
  "anonymous_user_id": "usrp_8K2M",
  "session_id": "ses_4D7Q",
  "platform": "ios",
  "app_version": "2.0.0",
  "locale": "ko-KR",
  "source": "server",
  "properties": {
    "goal_id": "goal_6C9R",
    "candidate_id": "cand_standard",
    "goal_domain": "saving",
    "goal_type": "increase",
    "difficulty": "standard",
    "duration_days": 30,
    "data_state": "fresh",
    "calculation_version": "goal-calc-1.0.0",
    "consent_version": "goal-confirm-1.0.0"
  }
}
```

### 공통 필드

| 필드 | 형식·허용값 | 규칙 |
| --- | --- | --- |
| `event_id` | 불투명 고유 문자열 | 재시도에도 동일한 값 유지 |
| `event_name` | 소문자 snake_case | 승인된 레지스트리 값만 허용 |
| `schema_version` | SemVer | 생산자는 명시, 소비자는 호환 버전만 처리 |
| `occurred_at`, `received_at` | UTC ISO 8601 | 지연 시간과 순서 오류 측정 |
| `anonymous_user_id` | 분석 전용 가명 키 | 인증 ID·원본 카드 키 금지; 탈퇴 시 조인 해제 |
| `session_id` | 앱 세션 키 | 30분 비활동 후 갱신 |
| `platform` | `ios`, `android`, `web` | 소문자 열거형 |
| `app_version` | SemVer | 배포 회귀 분석에 사용 |
| `locale` | BCP 47 | MVP는 `ko-KR` |
| `source` | `client`, `server`, `worker` | 상태 확정은 서버·워커 이벤트 사용 |
| `properties` | 이벤트별 객체 | 추가 필드 거부, PII 린터 통과 |

공통 컨텍스트에 실험이 있으면 `experiment_id`, `variant_id`를 넣을 수 있다. 실험은 안전 가드레일을 완화할 수 없고 투자·동의·삭제 흐름을 대상으로 하지 않는다.

## 3. 핵심 퍼널 이벤트

| 이벤트 | 발생 주체·시점 | 필수 속성 |
| --- | --- | --- |
| `adventurer_card_impressed` | 클라이언트, 카드 면적 50% 이상이 1초 보인 최초 시점 | `card_id`, `position`, `goal_domain`, `card_kind`, `data_age_band`, `similarity_reason_codes` |
| `adventurer_growth_opened` | 클라이언트, 성장 방식 상세가 실제 열린 시점 | `card_id`, `entry_point`, `goal_domain`, `routine_count` |
| `routine_selected` | 클라이언트, 루틴 선택 CTA 완료 | `card_id`, `routine_id`, `goal_domain`, `routine_type`, `maintained_days_band` |
| `goal_candidates_generated` | 서버, 후보 계산 성공 | `candidate_set_id`, `card_id`, `routine_id`, `goal_domain`, `candidate_count`, `data_state`, `calculation_version`, `fallback_to_action_goal` |
| `goal_candidates_viewed` | 클라이언트, 후보 비교 화면 노출 | `candidate_set_id`, `candidate_count`, `goal_domain`, `data_state`, `ai_explanation_state` |
| `goal_confirmed` | 서버, 멱등 확정 성공 | `goal_id`, `candidate_id`, `goal_domain`, `goal_type`, `difficulty`, `duration_days`, `data_state`, `calculation_version`, `consent_version` |
| `linked_quest_started` | 서버, 활성 목표에 연결된 퀘스트 시작 성공 | `goal_id`, `quest_id`, `goal_domain`, `quest_kind`, `verification_source` |
| `verified_action_recorded` | 서버, 중복 제거된 검증 행동이 원장에 반영될 때마다 반복 발생 | `verification_id`, `goal_id`, `action_kind`, `verification_source`, `effective_at`, `data_state` |
| `first_verified_action_recorded` | 서버, 목표별 첫 `verified_action_recorded` 반영 시 한 번 발생하는 호환 이벤트 | `verification_id`, `goal_id`, `action_kind`, `verification_source`, `effective_at`, `days_since_confirmation`, `data_state` |
| `raid_stage_reached` | 서버, 단계 최초 도달 | `goal_id`, `raid_stage`, `days_since_confirmation`, `data_state`, `progress_band` |
| `app_foregrounded` | 클라이언트, 앱이 백그라운드·종료 상태에서 포그라운드가 될 때마다 발생 | `foreground_reason`, `session_sequence` |

`card_kind`는 `individual_anonymous` 또는 `group_routine`이다. `progress_band`는 `0`, `1-39`, `40-69`, `70-99`, `100`만 허용하고 정확 진행률은 제품 DB에서 조회한다. `maintained_days_band`는 `30-89`, `90-179`, `180-364`, `365+`로 제한한다.

`verified_action_recorded.effective_at`은 금융 행동이 실제 효력을 가진 시각을 UTC ISO 8601로 기록하고 `occurred_at`은 검증 이벤트가 생성된 시각을 유지한다. `verification_id`와 `event_id`는 같은 원장 행동의 재처리에서 고정하므로 지연 수집·재전송은 행동 수를 늘리지 않는다. `first_verified_action_recorded` ID는 기존 소비자를 위해 보존하며 반복 행동이나 4주 유지 계산에는 `verified_action_recorded`를 사용한다.

`app_foregrounded`는 현재 `PRODUCT_ANALYTICS=GRANTED`인 경우에만 생성한다. 동의 전 버퍼를 사후 전송하지 않고 철회 즉시 신규 생성을 중지한다. 공통 필드 외 화면·라우트·푸시 내용·금융 상태를 넣지 않으며, 푸시 수신이나 백그라운드 작업은 포그라운드로 간주하지 않는다. 30분 비활동 후 새 `session_id`를 발급하고 같은 세션의 재포그라운드는 `session_sequence`만 증가시킨다.

## 4. 상태·안전·운영 이벤트

### 목표와 데이터

| 이벤트 | 발생 조건 | 필수 속성 |
| --- | --- | --- |
| `goal_candidate_rejected` | 사용자가 후보를 선택하지 않고 거절 이유 제출 | `candidate_set_id`, `reason_code`, `goal_domain`, `difficulty_shown` |
| `goal_paused` | 서버 상태가 `PAUSED`로 전이 | `goal_id`, `reason_code`, `days_active` |
| `goal_resume_blocked` | 30일 초과 정지·20% 기준선 변화·데이터 문제로 재확인 필요 | `goal_id`, `reason_code`, `data_state` |
| `goal_cancelled` | 서버 상태가 `CANCELLED`로 전이 | `goal_id`, `reason_code`, `days_active` |
| `goal_completed` | 진행률 100% 검증 | `goal_id`, `days_to_complete`, `goal_domain`, `data_state` |
| `goal_expired` | 기간 종료 후 `EXPIRED` 전이 | `goal_id`, `days_active`, `highest_progress_band` |
| `data_state_changed` | 데이터 신뢰 상태 변경 | `from_state`, `to_state`, `reason_code`, `surface` |
| `calculation_discrepancy_reported` | 사용자가 분류·계산 오류 신고 | `surface`, `calculation_version`, `reason_code`, `data_state` |

`reason_code`는 이벤트별 승인 목록을 사용하며 자유 서술은 고객지원 시스템에 별도 보관한다.

### AI

| 이벤트 | 발생 조건 | 필수 속성 |
| --- | --- | --- |
| `ai_explanation_requested` | AI 게이트웨이 호출 직전 | `ai_request_id`, `task`, `prompt_version`, `model_version` |
| `ai_explanation_validated` | 구조화·정책 검증 통과 | `ai_request_id`, `latency_ms`, `recommended_difficulty` |
| `ai_fallback_served` | AI 대신 결정론적 문구 노출 | `ai_request_id`, `reason_code`, `task`, `latency_ms` |
| `ai_output_blocked` | ID·수치·공급 근거·요약·안전 검증 실패 | `ai_request_id`, `reason_code`, `prompt_version`, `model_version` |
| `ai_issue_reported` | 사용자 신고 접수 | `ai_request_id`, `reason_code`, `surface` |

AI 이벤트에는 프롬프트·응답 원문과 후보 표시 금액을 넣지 않는다.

### 동의·개인정보

| 이벤트 | 발생 조건 | 필수 속성 |
| --- | --- | --- |
| `consent_decision_recorded` | 동의 부여·거부를 서버가 저장 | `consent_id`, `consent_version`, `decision`, `surface` |
| `consent_withdrawal_requested` | 철회 접수 | `withdrawal_id`, `consent_id`, `consent_version` |
| `consent_withdrawal_effective` | 조회 경로에서 추천 제외 완료 | `withdrawal_id`, `consent_id`, `elapsed_seconds` |
| `derived_data_deleted` | 캐시·인덱스·파생 추천 제거 완료 | `withdrawal_id`, `data_scope`, `elapsed_seconds`, `result` |
| `account_deletion_completed` | 서비스 데이터 삭제 작업 완료 | `deletion_id`, `elapsed_hours`, `retained_legal_record` |

동의 이벤트는 필수 운영 감사 스트림과 선택 제품 분석 스트림을 분리한다. 사용자가 제품 분석을 거부해도 동의 철회·삭제 SLA는 운영 스트림으로 측정한다.

### 게임화 안전

| 이벤트 | 발생 조건 | 필수 속성 |
| --- | --- | --- |
| `raid_motion_preference_changed` | 연출 강도 변경 | `from_level`, `to_level`, `surface` |
| `notification_sent` | 허용 푸시 발송 | `notification_kind`, `user_scheduled`, `delivery_result` |
| `prohibited_investment_incentive_detected` | 빌드·콘텐츠 검사에서 투자 유도 보상 발견 | `surface`, `rule_id`, `release_version`, `blocked_before_exposure` |
| `social_pressure_feedback_submitted` | 비교 압박·수치심 응답 | `surface`, `pressure_level`, `shame_level`, `goal_domain` |

금지된 경제 보상이나 투자 유도 문구가 실제 노출된 경우 분석 이벤트만 남기고 끝내지 않는다. 보안·제품 사고를 생성하고 배포를 중지한다.

## 5. KPI 정의와 검증 단계

모든 핵심 퍼널 KPI는 코호트의 첫 카드 노출일을 D0으로 잡고 28일 관찰한다. 실제 서비스 집계에서는 개발·내부 QA·자동화 계정과 이벤트 동의가 없는 사용자를 제외한다. 같은 사용자의 중복 이벤트는 각 단계의 최초 발생만 사용한다.

아래 수치는 **post-MVP 제품 검증을 위한 목표 가설**이며 MVP 합격선이 아니다. MVP에서는 고정 시계·합성 이벤트 픽스처로 분자, 분모, 제외 조건, 시간대, 중복 제거와 관찰창 변환이 기대값과 정확히 일치하는지만 인수한다. 실제 마이데이터 연동, 100명 또는 그 밖의 실제 사용자 표본, 4주 파일럿 결과는 모두 post-MVP 별도 제안이다.

| KPI | 공식 | 관찰창 | post-MVP 목표 가설 |
| --- | --- | --- | --- |
| 성장 방식 보기 전환율 | `adventurer_growth_opened` 사용자 / `adventurer_card_impressed` 사용자 | 노출 후 7일 | 35% 이상 |
| 루틴 선택률 | `routine_selected` 사용자 / `adventurer_growth_opened` 사용자 | 상세 후 7일 | 45% 이상 |
| 후보 확인률 | `goal_candidates_viewed` 사용자 / `routine_selected` 사용자 | 선택 후 24시간 | 90% 이상 |
| 목표 확정률 | `goal_confirmed` 사용자 / `goal_candidates_viewed` 사용자 | 후보 확인 후 7일 | 40% 이상 |
| 연결 퀘스트 시작률 | `linked_quest_started` 사용자 / `goal_confirmed` 사용자 | 확정 후 7일 | 50% 이상 |
| 검증된 첫 행동 완료율 | `first_verified_action_recorded` 사용자 / `goal_confirmed` 사용자 | 확정 후 14일 | 40% 이상 |
| 레이드 1단계 도달률 | `raid_stage_reached(stage=1)` 사용자 / `goal_confirmed` 사용자 | 확정 후 28일 | 35% 이상 |
| 2·3단계 도달률 | 단계별 최초 도달 사용자 / `goal_confirmed` 사용자 | 확정 후 28일 | 탐색 지표, 단계별 보고 |
| 4주 습관 유지율 | 4개 서로 다른 목표 상대 주차에 `verified_action_recorded`가 1회 이상인 사용자 / 28일 관찰이 끝난 `goal_confirmed` 사용자 | 확정 후 28일 | 25% 이상 |
| D7 재방문율 | D7에 `app_foregrounded`가 있는 사용자 / D0 신규 가치 코호트 | D7 KST 하루 | 25% 이상 |
| D28 재방문율 | D28에 `app_foregrounded`가 있는 사용자 / D0 신규 가치 코호트 | D28 KST 하루 | 12% 이상 |

`D0 신규 가치 코호트`는 관찰 기간에 최초로 `adventurer_card_impressed`가 발생한 사용자의 KST 날짜다. D7과 D28은 각각 `[D0+7일 00:00, D0+8일 00:00)`, `[D0+28일 00:00, D0+29일 00:00)` KST 구간의 `app_foregrounded`로 계산한다. 한 사용자의 여러 세션은 1명으로 축약하며 푸시 수신만으로 계산하지 않는다.

4주 유지율의 주차는 목표 확정 시각부터 7일 고정 구간으로 정의한다. `effective_at`이 `[확정, +7일)`, `[+7일, +14일)`, `[+14일, +21일)`, `[+21일, +28일)`에 각각 하나 이상 있고 관찰 종료 시각이 확정 후 28일 이상인 사용자만 분자에 포함한다. 늦게 수집된 행동도 `occurred_at`이 아니라 `effective_at` 구간으로 들어가며 같은 `verification_id`는 한 번만 센다.

MVP KPI 변환 인수는 `KPI-TRANSFORM-001`을 포함한 합성 골든 픽스처로 수행하고, 실제 목표 가설 달성 여부를 주장하지 않는다.

## 6. 가드레일과 중지 기준

| 지표 | 공식 | 경고 | 출시·확대 중지 |
| --- | --- | --- | --- |
| 목표 거절·취소율 | 후보 거절 또는 7일 내 취소 사용자 / 후보 확인 사용자 | 35% 초과 | 45% 초과 |
| 과도한 목표 응답률 | 적절성 설문 1~2점 사용자 / 응답 사용자 | 15% 초과 | 25% 초과 |
| 비교 압박·수치심률 | 압박 또는 수치심 4~5점 사용자 / 응답 사용자 | 10% 초과 | 20% 초과 |
| 계산 불일치 신고율 | 고유 신고 사용자 / 목표 활성 사용자 | 1% 초과 | 2% 초과 또는 심각 오류 1건 |
| 공개 철회 조회 차단 P95 | `effective - requested` | 3분 초과 | 5분 초과 |
| 공개 철회 완전 제거 최대 | 모든 `derived_data_deleted` 완료 시간 | 12시간 초과 | 24시간 초과 1건 |
| AI 폴백률 | `ai_fallback_served` / AI 요청 | 5% 초과 | 15% 초과, AI 기능만 중지 |
| AI 잘못된 ID·수치 노출 | 실제 사용자 노출 건수 | 해당 없음 | 1건 |
| 투자 유도 문구·보상 노출 | 실제 사용자 노출 건수 | 해당 없음 | 1건 |
| 비허용 개인정보 이벤트 수집 | 격리 전·후 탐지 건수 | 해당 없음 | 1건 |

중지 기준은 핵심 KPI 상승보다 우선한다. MVP에서는 합성 장애 주입으로 경보와 차단 동작을 인수하고, 실제 노출·비율 기반 확대 판단은 post-MVP 단계에서 적용한다. 중지 시 결정론적 폴백이나 해당 기능 비활성화로 핵심 흐름을 유지하고, 원인·영향·재발 방지 테스트가 승인되기 전 확대하지 않는다.

## 7. 데이터 품질과 거버넌스

MVP에서는 7일·28일 이력과 배포 전후 구간을 고정 시계 합성 픽스처로 생성해 아래 규칙을 검증하며 실제 시간이 지나기를 기다리지 않는다. 실제 일별 감시와 병렬 관찰은 post-MVP 운영 규칙이다.

- 이벤트 레지스트리 변경은 제품, 데이터, 개인정보 담당자 리뷰를 받는다.
- CI에서 이름·필수 속성·열거형·추가 필드 금지·PII 금지 패턴을 계약 테스트한다.
- 일별로 수집 지연 P95 5분 이하, 필수 필드 완전성 99.9% 이상, 중복률 0.1% 이하, 서버·클라이언트 단계 역전률 0.5% 이하를 검사한다.
- 배포 전후 24시간 이벤트량과 전환율을 이전 7일 같은 요일 중앙값과 비교하고 30% 이상 급변하면 계측 회귀를 먼저 조사한다.
- 대시보드는 `event_name`, `schema_version`, KPI SQL 버전, 최종 갱신 시각과 제외 조건을 표시한다.
- KPI 쿼리 변경은 이전 버전과 28일 병렬 계산해 차이를 설명하고 데이터 책임자가 승인한다.
- 원시 제품 분석 이벤트는 13개월, 사용자 수준 파생 테이블은 6개월 보관 후 집계 또는 삭제한다. 더 짧은 법적·동의 기준이 있으면 그 기준을 우선한다.

## 8. 필수 대시보드

1. **가치 퍼널:** 전체 및 목표 영역별 8단계 전환, 단계 간 중앙 소요시간, 데이터 상태별 이탈.
2. **목표 품질:** 난이도별 확정·거절·7일 내 취소, 첫 행동, 4주 유지, 재설정 필요율.
3. **안전:** 과도함·압박·수치심, 계산 불일치, 투자 유도 탐지, AI 차단·폴백·오노출.
4. **개인정보 운영:** 공개 동의·철회, 조회 차단 P50/P95/최대, 파생 삭제 완료, SLA 위반.
5. **데이터 건강:** 이벤트 지연·누락·중복·스키마 오류와 앱 버전별 계측 급변.

표본 30명 미만인 세그먼트는 사용자 수준으로 드릴다운하지 않고 값 대신 `표본 부족`으로 표시한다. 대시보드 내보내기에는 가명 사용자 키를 포함하지 않는다.

MVP 대시보드는 합성 픽스처임을 명시하고 실제 제품 KPI처럼 해석하거나 외부 공유하지 않는다. 실제 사용자 대시보드 운영은 post-MVP 동의·연동 검토 후 시작한다.
