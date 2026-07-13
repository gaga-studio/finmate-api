# FinMate vNext product requirements

## 1. Product promise

FinMate helps a financially inexperienced young adult discover how similar anonymous adventurers sustain a routine, adapt one routine to their own baseline, and connect verified financial behavior to quests, a raid, and a daily journey record.

The service essence is:

> 금융진입장벽을 느끼는 청년들에게 사회적 호기심과 동기를 유발하여 금융친화력을 길러준다.

## 2. Release persona and fixture

- The release target is a 19–34-year-old who wants to manage money but has difficulty choosing a first action.
- The representative demo starts in July 2026 with `2000000` KRW saved toward `5000000` KRW for `유럽여행경비` by January 2027.
- Six synthetic `500000` KRW automatic-savings events occur from August 2026 through January 2027. Only those verified events move the goal from 2M to 5M KRW.
- Friend, mate, comparison, financial, and product examples are synthetic fixtures. No fixture implies investment return.

## 3. Navigation and onboarding

Authenticated navigation has exactly four tabs: `홈 · 메이트 · 퀘스트 · 기록`. Settings are reached from the home header.

Onboarding is ordered:

`생활맥락 → 돈 고민 → 금융성향 → 생활태그·익명 공개범위 → 합성 마이데이터 연결 → 기준선 진단`

At the baseline diagnosis the user chooses one of two outcomes:

- `GOAL_ACTIVE`: choose a goal type, name it, review current value, target value and target month, then explicitly confirm it.
- `EXPLORE_ONLY`: defer goal confirmation and enter the product in exploration mode.

Exploration mode can read friend, group and anonymous-adventurer discovery content. Raid start, quest acceptance, routine import and personalized Hana product information are locked until a goal is confirmed.

## 4. Required flows

### 4.1 Goal and home

1. Goal confirmation is a separate command after onboarding profile completion.
2. A user has at most one active main goal.
3. Explore-only home shows an empty arena, why progress is locked, and one `목표 설정` action.
4. Goal-active home shows total assets, goal progress, raid, four character stats, active routine, recommended quest, data state and last sync time.
5. Tapping the boss preserves goal context and opens quests. Tapping a character opens its report:
   - bear: `소비 방어력`
   - seal: `저축 HP`
   - rabbit: `투자 판단력`
   - bird: `퀘스트 XP`
6. Each character report shows the actual input value, calculation reason, 30-day trend, data state, and one next action. Rabbit content never includes holdings, returns, or trade recommendations.

### 4.2 Quest acceptance and evidence

1. The first quest visit may have zero active quests while still showing one boss-linked recommendation.
2. The demo accepts `이번 달 저축 가능액 확인하기` before mate discovery.
3. A quest moves from `AVAILABLE` to `ACTIVE` only through explicit acceptance.
4. Behavior completion grants integer XP and approved non-cash internal rewards.
5. A financial-evidence quest moves to `DATA_PENDING` until synthetic MyData verifies the event.
6. Quest actions never directly change financial stats, goal progress, or raid HP.

### 4.3 Mate discovery

The mate tab contains three subareas:

- `친구`: synthetic, read-only friend completion status, amount-free activity feed, three-stat comparison, routine preview and shared streak.
- `메이트 찾기`: similar-group summary, group allocation averages, goal-achievement groups, reviewed popular routines, coach copy, and group members.
- `비교 탐색`: predefined combinations of age, occupation, income range, spending tendency, savings-rate range and investment tendency, returning synthetic anonymous adventurers.

The representative path is:

`메이트 찾기 → 유럽여행 목표 달성 그룹 → 그룹 상세 → 익명 모험가 → 모험가 리포트 → 빌드 따라하기`

Cards expose only anonymous context tags, ranged indicators, routine duration and verification date. Exact balance, transaction source, employer, detailed location, financial product used by the peer, investment holding and rank are forbidden.

### 4.4 Routine recommendation and import

1. The selected adventurer routine is adapted to the user's confirmed goal and current baseline.
2. The UI first shows one `recommendedCandidate` and its deterministic reason.
3. `LIGHT`, `STANDARD` and `CHALLENGE` remain selectable intensity options but the user is not forced to compare all three.
4. The representative recommendation is `월급날 먼저 저축`, adapted to a `월 500000원` saving subquest.
5. Import creates one active routine build and a linked subquest; it never changes the main goal.
6. If a build is already active, confirmation shows current and proposed builds. Cancel preserves the current build; confirmation archives it and activates the new one atomically.

### 4.5 Related Hana product information

- A related Hana product is selected from a reviewed catalog by routine category, not copied from the adventurer.
- The screen shows product name, key conditions, information date, cautions and an official information link.
- Before compliance approval, no signup or application action exists.
- Product viewing never grants XP, changes a stat, creates financial evidence, or changes raid progress.
- Explore-only users may see generic educational content but not a personalized product card.

### 4.6 Daily journey record and demo

- `기록` opens a monthly daily stepping-stone journey rather than a generic calendar dashboard.
- Each day shows one representative activity, at most two supporting activity labels and `+N` for the remainder.
- Tapping a day opens a bottom sheet with income, spending, saving, investment, quest, budget and recalculation evidence for that date.
- The demo endpoint exists only under the backend `demo` profile and synthetic users. One request returns July-to-January timeline frames; the UI animates six monthly 500k savings events.
- Production has no time-advance operation or control.

## 5. Data, AI and safety

- Financial calculations use integer KRW, basis-point ratios and versioned deterministic code.
- Every calculated read includes `calculationVersion`, `dataState` and `lastSyncedAt`.
- Coach messages use approved deterministic copy. No runtime LLM is used in this release.
- `FRESH`, `PENDING`, `STALE` and `INSUFFICIENT` are visible product states, not hidden implementation details.
- Investment behavior is limited to risk-profile, diversification and report-review learning actions.
- No public ranking, loss penalty, cash-equivalent reward, return projection, trade execution, email verification or password recovery is included.

## 6. Acceptance criteria

- A user can finish onboarding without a goal and explore mate content.
- Goal-required commands return RFC 7807 `GOAL_REQUIRED` with HTTP 409 in explore-only mode.
- A goal-active user can open four distinct character reports and accept a boss-linked quest.
- Friend and comparison screens are synthetic and read-only; the core mate path reaches an adventurer report and routine recommendation.
- Routine recommendation prioritizes one candidate and offers three optional intensities.
- Routine import does not mutate the main goal and replacement requires explicit confirmation.
- Product information contains review metadata and has no application flow or progress side effect.
- Quest acceptance or behavior completion alone does not move raid progress.
- Six verified 500k savings events move the demo fixture exactly from 2M to 5M KRW and complete the raid in January 2027.
- Journey nodes, daily bottom-sheet totals and monthly summaries reconcile.
