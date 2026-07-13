# FinMate vNext domain contract

## 1. Shared value types

- `KrwAmount`: signed 64-bit JSON integer in whole KRW.
- `BasisPoints`: integer `0..10000`; `10000` means 100%.
- `Timestamp`: ISO 8601 date-time with an offset or `Z`.
- `DataState`: `FRESH | PENDING | STALE | INSUFFICIENT`.
- Every calculated read requires `calculationVersion`, `dataState`, and nullable `lastSyncedAt`.

The synthetic source combines the checksum-locked `gaga-studio/finmate-data` `v1.0.0`
L1/L2 bundle at `63ca3d0` with the corrected L3 tree at `22243bc`. Bundle archive SHA,
bundle commit, L3 commit and L3 tree SHA are independent provenance fields. Imported L2
records are allowlisted inputs; `metrics_monthly`, `stats` and `stats_history` are test
oracles rather than runtime truth. Financial metrics, stats and goal progress are
recalculated by backend code.

## 2. OnboardingState and UserGoal

`OnboardingState` is `EXPLORE_ONLY | GOAL_ACTIVE`. Profile completion and goal confirmation are separate boundaries.

- **ONB-1:** Completing context, consent, synthetic MyData connection and baseline diagnosis creates `EXPLORE_ONLY` unless an active goal already exists.
- **ONB-2:** Explore-only users can read mate discovery but cannot start a raid, accept a quest, import a routine or receive personalized product information.
- **UG-1:** A user has zero or one active `UserGoal`; confirming a second active goal conflicts.
- **UG-2:** Only the goal-confirmation command creates a goal and moves the onboarding state to `GOAL_ACTIVE`.
- **UG-3:** Mate discovery, routine import, product viewing and quest actions never create or mutate the main goal.
- **UG-4:** The demo goal is `유럽여행경비`, `2000000` current KRW, `5000000` target KRW, `2027-01` target month.

Goal-required commands return RFC 7807 code `GOAL_REQUIRED` with HTTP 409 when the user is explore-only.

## 3. HomeView, RaidView and CharacterReport

`HomeView` is a mode-aware read model. Explore-only home contains no goal or raid and provides a goal-setup action. Goal-active home includes goal, raid, stats, active routine, recommended quest, data state and sync time.

`RaidView` is calculated only from the active goal and verified synthetic financial evidence.

- **RAID-1:** Quest acceptance, quest behavior completion and product information events cannot change boss HP or financial stats.
- **RAID-2:** Current and highest verified progress are distinct; stale or insufficient data cannot be presented as new progress.
- **RAID-3:** XP is displayed separately from financial stats.

`CharacterReport` has one of four types:

- `SPENDING_DEFENSE`: bear, discretionary spending and budget stability.
- `SAVING_HP`: seal, saving inflow and emergency-fund continuity.
- `INVESTMENT_JUDGMENT`: rabbit, risk-profile alignment, diversification check and learning activity only.
- `QUEST_XP`: bird, accepted and completed behavior quests.

Every report includes actual input summary, calculation reason, 30-day trend, next action and calculated metadata. Investment reports forbid holdings, product or stock names, returns and trading recommendations.

## 4. Mate discovery models

`MateFriendOverview`, the friend feed and shared streaks are synthetic read-only projections. Activity is amount-free and cannot reveal source-user identifiers. No social write command exists in MVP. Source friend aggregation distinguishes `friendCount` (all accepted friends) from `scoredFriendCount` (friends whose ratio-based stats are defined). These counts must never be presented as interchangeable denominators.

`MateGroupReport` exposes group criteria, eligible anonymous sample count, ranged spending/saving allocation, three-stat distribution, reviewed routine summaries and deterministic coach copy.

`RecommendedAdventurerCard` and `AdventurerReport` expose anonymous context tags, similarity reasons, goal-achievement state, ranged indicators, routine duration and verification date.

- **MATE-1:** Operational aggregation requires at least 30 eligible members. Smaller demo groups require `syntheticDemo = true` and are excluded from production aggregation.
- **MATE-2:** Discovery cards and group reports expose no exact financial values. A
  separate `PublicFinancialProfile` may expose only fields covered by the source
  user's active granular consent. Rank, source identity, account number, raw
  transaction text, detailed employer/location and authentication identifiers are
  forbidden regardless of consent.
- **MATE-3:** Direct comparison accepts only server-approved filter combinations; unsupported combinations are rejected rather than silently broadened.

### 4.1 DisclosureConsent and PublicFinancialProfile

`DisclosureConsent` is private by default and independently controls assets, income,
spending, savings, products, investment holdings and trade history. Update requires a
preview and exact-disclosure confirmation. Withdrawal moves the profile out of public
reads and recommendation eligibility immediately.

`PublicFinancialProfile` is a read model assembled from active consent. It may include
exact KRW values, product names, ticker holdings and BUY/SELL records, but those values
are information-only.

- **DISC-1:** A non-consented category is absent, not zero-filled or inferred.
- **DISC-2:** Public products, holdings and trades cannot become a routine target,
  quest, reward, evidence source or raid input.
- **DISC-3:** Account number, raw transaction text, detailed employer/location,
  authentication identifiers and source-user identity are permanently excluded.
- **DISC-4:** Withdrawal removes the profile from direct reads, recommendation cards
  and process-local cache without a fallback snapshot.

## 5. RoutineRecommendation and ActiveRoutineBuild

`RoutineRecommendation` belongs to one source adventurer routine and contains:

- one `recommendedCandidate` reference with deterministic recommendation reason;
- `intensityOptions` containing exactly one `LIGHT`, one `STANDARD`, and one `CHALLENGE` candidate for optional adjustment;
- an optional reviewed related-product reference that is separate from the routine.

The UI may accept the recommendation without comparing every intensity.

- **RR-1:** Spending and saving may use amount, ratio or behavior targets. Investment judgment is behavior-only.
- **RR-2:** The recommendation is calculated from the user's active goal and baseline; it never copies the peer's exact amount.
- **ARB-1:** Import requires `GOAL_ACTIVE` and creates at most one global active build plus a linked subquest.
- **ARB-2:** Existing build replacement requires explicit confirmation. Confirmation archives the old build and activates the new one atomically; cancel changes nothing.
- **ARB-3:** Build activity never mutates the active goal directly.

## 6. RelatedHanaProductInfo

`RelatedHanaProductInfo` comes from a reviewed catalog and contains product display name, category, key terms, information date, cautions, official information URL and `affectsProgress = false`.

- Product matching uses routine category, not the adventurer's product.
- No application, enrollment or purchase command exists before compliance approval.
- View events cannot create XP, quest evidence, financial stats or raid progress.

## 7. Quest

`Quest` lifecycle is `AVAILABLE -> ACTIVE -> DATA_PENDING | COMPLETED | EXPIRED | CANCELLED`.

- **Q-1:** `accept` is an explicit command and requires an active goal.
- **Q-2:** Behavior-only completion may grant integer XP and fixed internal points
  immediately. XP affects character level only; points buy deterministic cosmetics only.
- **Q-3:** Financial-evidence completion remains `DATA_PENDING` until synthetic MyData verifies it.
- **Q-4:** Quest actions do not change spending, saving or investment-judgment stats directly.
- **Q-5:** An imported routine may create one linked subquest while preserving the initial boss-linked quest.

`PointLedgerEntry` is append-only and idempotent per reward source. The cosmetic
catalog contains only `OUTFIT`, `PROFILE_FRAME` and `THEME` items. Coupons, cash
conversion, user-to-user transfer, random boxes and core-report locks do not exist.
Investment amount, trade volume, return and product signup cannot award XP or points.

## 8. DailyJourneyMonth and DailyRecord

`DailyJourneyMonth` contains month, recorded-day count, monthly income/spending/saving summaries, ordered stepping-stone nodes and calculated metadata.

Each node has a date, one representative activity, up to two supporting activity labels, remaining count, current/future state and detail link. Representative financial activity uses the largest absolute amount; quests do not participate in that amount comparison.

`DailyRecord` contains all income, spending, saving, investment, quest, budget and recalculation events for one date. Summary totals and event totals must reconcile. Reflection is separate user text and never modifies calculations.

## 9. Demo timeline

The deterministic demo starts in July 2026 at 2M KRW. Synthetic automatic savings of 500k are verified in August, September, October, November, December and January. The January frame reaches exactly 5M KRW and completes the raid.

- The timeline operation is available only under the backend `demo` profile and synthetic demo users.
- The server returns ordered frames; the web app renders them and performs no financial calculation.
- Production has no timeline-advance route.

## 10. First-release adapters and exclusions

- Auth: email/password, bearer access token JSON and rotating opaque refresh cookie.
- Financial provider: `SYNTHETIC`.
- Coach provider: `DETERMINISTIC_APPROVED_COPY`.
- No runtime generation, real MyData, brokerage execution, public ranking, loss penalty, cash reward or product application domain exists.
