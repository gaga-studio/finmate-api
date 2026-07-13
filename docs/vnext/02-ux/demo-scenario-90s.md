# FinMate deterministic 90-second demo scenario

## Fixed fixture

- Start: July 2026, Europe travel fund `2,000,000 KRW`
- Target: `5,000,000 KRW` by January 2027
- Imported routine: `월급날 먼저 저축`
- Generated subquest: `매월 500,000원 자동저축`
- Verified savings frames: August, September, October, November, December, January
- Final state: `5,000,000 KRW`, raid completed

## Timeline

| Time | Screen | Presenter-visible event | Required state |
| ---: | --- | --- | --- |
| 0–13s | Onboarding montage | Context, concern, tendency, tags, synthetic MyData, baseline | No active goal yet |
| 13–19s | Goal confirmation | Choose money-saving goal, name `유럽여행경비`, confirm 2M→5M and Jan 2027 | `GOAL_ACTIVE` |
| 19–29s | Home | Raid starts; bear, seal, rabbit and bird reports switch quickly | Four distinct report types |
| 29–37s | Quests | Tap boss, show zero active, accept available-savings check | Quest becomes `ACTIVE`; raid unchanged |
| 37–51s | Mate | Show friend, mate finding, direct compare; open travel-goal group and adventurer | Synthetic read-only labels visible where applicable |
| 51–64s | Routine | Open report, select payday-first-saving, accept recommended 500k subquest | Active build created; goal unchanged |
| 64–71s | Hana information | Open key terms, date and cautions, then return | No signup; no progress change |
| 71–84s | Record | Animate July→January and six verified 500k events | Ordered server frames; daily stepping-stone layout |
| 84–90s | Home completion | Show 5M target, defeated boss and verified cumulative summary | Completed goal and raid |

## Recording rules

- The sequence is deterministic and network-independent.
- The web app renders values returned by fixtures and performs no arithmetic.
- Product information viewing emits analytics only; it does not create a quest, reward or financial event.
- The final frame states that progress came from verified synthetic financial data.
