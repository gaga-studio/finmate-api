# FinMate vNext information architecture and flows

## Navigation tree

```text
앱 진입
├─ 회원
│  ├─ 회원가입
│  └─ 로그인
├─ 온보딩
│  ├─ 생활맥락
│  ├─ 돈 고민
│  ├─ 금융성향
│  ├─ 생활태그·익명 공개범위
│  ├─ 합성 마이데이터 연결
│  ├─ 기준선 진단
│  └─ 목표 선택
│     ├─ 목표 설정·확정
│     └─ 일단 탐색하기
└─ 메인
   ├─ 홈
   │  ├─ 탐색 모드 빈 레이드
   │  ├─ 목표·레이드
   │  ├─ 4동물 금융 상태
   │  ├─ 분야별 리포트
   │  ├─ 활성 루틴
   │  ├─ 추천 퀘스트
   │  └─ 데이터 동기화 상태
   ├─ 메이트
   │  ├─ 친구
   │  │  ├─ 오늘 완료 현황
   │  │  ├─ 금액 없는 금융 근황
   │  │  ├─ 친구 vs 나 3스탯 비교
   │  │  └─ 루틴·연속기록
   │  ├─ 메이트 찾기
   │  │  ├─ 내 유사그룹
   │  │  ├─ 그룹 배분 평균
   │  │  ├─ 목표 달성 그룹
   │  │  ├─ 그룹 상세·분포
   │  │  └─ 익명 모험가 목록
   │  └─ 비교 탐색
   │     ├─ 검수된 조건 조합
   │     └─ 추천 익명 모험가
   │
   │  모험가 공통 상세 흐름
   │  └─ 모험가 상세
   │     └─ 모험가 리포트
   │        └─ 빌드 따라하기
   │           ├─ 추천 서브퀘스트
   │           ├─ 강도 변경
   │           ├─ 루틴 적용·교체
   │           └─ 관련 하나 상품 정보
   ├─ 퀘스트
   │  ├─ 요약
   │  ├─ 진행 중
   │  ├─ 참여 가능
   │  ├─ 데이터 반영 대기
   │  ├─ 완료
   │  └─ 비환금성 내부 포인트
   └─ 기록
      ├─ 월 이동·월간 요약
      ├─ 일별 대형 발판
      ├─ 일일 기록 바텀시트
      ├─ 금융데이터 재계산
      └─ 회고·월간 요약

홈 헤더
└─ 설정

시연 전용
├─ 7월→1월 시간 진행
└─ 레이드 완료 연출
```

The bottom navigation is exactly `홈 · 메이트 · 퀘스트 · 기록`. Settings and demo controls are not tabs.

## Access matrix

| Capability | `EXPLORE_ONLY` | `GOAL_ACTIVE` |
| --- | --- | --- |
| View friend, groups and anonymous adventurers | Allowed | Allowed |
| View empty home and goal CTA | Allowed | Not applicable |
| Start or progress raid | Locked | Allowed from verified data |
| Accept quest | Locked | Allowed |
| Import routine | Locked | Allowed |
| View personalized Hana product | Locked | Allowed after routine recommendation |
| View generic financial education | Allowed | Allowed |

Locked commands explain that a confirmed goal is required and route to goal setup without discarding exploration context.

## Core flows

### A. First run and goal choice

`회원 → 온보딩 1~5 → 기준선 진단 → 목표 설정 또는 일단 탐색하기`

- Goal setup: `자금 모으기 → 유럽여행경비 → 2,000,000 / 5,000,000 KRW → 2027-01 → 명시적 확정 → 홈 레이드`.
- Explore: `일단 탐색하기 → 탐색 모드 홈 → 메이트 읽기`. The draft may be resumed later.

### B. Home and quest

`홈 레이드 → 곰·물개·토끼·새 리포트 전환 → 보스 탭 → 퀘스트 → 진행 중 0개 → 추천 퀘스트 상세 → 수락`

The initial recommendation is `이번 달 저축 가능액 확인하기`. Accepting it creates an active quest but does not move the raid.

### C. Mate and routine

`메이트 친구 → 메이트 찾기 → 비교 탐색 → 유럽여행 목표 달성 그룹 → 익명 모험가 → 모험가 리포트 → 월급날 먼저 저축 → 추천 월 500,000원 → 적용`

The three subareas are visible during the demo, but `메이트 찾기` is the core functional route. Friend and direct comparison are synthetic read-only fixtures.

### D. Product information

`루틴 적용 결과 → 관련 하나 상품 정보 → 조건·기준일·유의사항·공식 링크 확인 → 가입 없이 복귀`

The card comes from a reviewed catalog and is not the peer's product. Open and close events have no gamification or financial side effect.

### E. Record and verified completion

`기록 → 7월 발판 → 8월~1월 월 전환 → 월별 500,000원 자동저축 증거 → 홈 → 5,000,000원·레이드 완료`

Day nodes summarize; the bottom sheet owns complete daily detail. The UI uses returned timeline frames and never calculates the goal locally.

## 90-second deterministic demo

| Time | Screen and action |
| ---: | --- |
| 0–13s | Onboarding montage through baseline diagnosis |
| 13–19s | Confirm Europe travel goal, 2M→5M KRW, January 2027 |
| 19–29s | Home raid and four character reports |
| 29–37s | Boss to quests; accept available-savings check |
| 37–51s | Friend, mate finding and comparison; select travel-goal adventurer |
| 51–64s | Adventurer report; import payday-first-saving as 500k monthly subquest |
| 64–71s | Inspect reviewed Hana product information and return |
| 71–84s | Animate July→January journey and six verified savings events |
| 84–90s | Show 5M goal and completed raid |

## State behavior

- `PENDING`: behavior is done; financial verification is waiting.
- `STALE`: show last sync time and one recovery action; do not progress.
- `INSUFFICIENT`: explain missing evidence and offer a behavior-only starting action.
- Routine replacement shows both builds; cancel preserves the current build.
- Demo-only time advancement is never linked or callable from production navigation.
