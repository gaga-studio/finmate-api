# FinMate vNext 팀 포털

> 상태: Review  
> 기준일: 2026-07-12  
> 원본: Git `docs/vnext/`  
> 용도: Notion 첫 화면에 붙여 넣는 팀 안내 페이지

## 이번 재개발에서 만드는 것

FinMate vNext는 나와 비슷한 익명 모험가의 검증된 금융 루틴을 발견하고, 그 루틴을 내 기준선에 맞는 목표로 바꾼 뒤 실제 금융행동의 변화를 자동 레이드와 기록으로 보여주는 모바일 PWA다.

```text
추천 모험가 → 성장 방식 → 루틴 선택 → 목표 후보 → 사용자 확정
→ 자동 레이드 → 목표 연결 퀘스트 → 데이터 동기화 → 30일 기록
```

## 먼저 읽을 문서

| 순서 | 문서 | 읽고 결정할 내용 |
| --- | --- | --- |
| 1 | `docs/vnext/01-product/project-charter.md` | 문제, 사용자, MVP, 성공 기준 |
| 2 | `docs/vnext/01-product/prd.md` | 제품 요구사항과 제외 범위 |
| 3 | `docs/vnext/02-ux/ia-and-user-flows.md` | 전체 흐름과 대표 시연 경로 |
| 4 | `docs/vnext/03-domain/calculation-policy.md` | 금융 계산의 기준과 예외 |
| 5 | `docs/vnext/03-domain/state-machines.md` | 목표·레이드·퀘스트·동기화 상태 |
| 6 | `docs/vnext/06-api/openapi.yaml` | 프론트엔드와 백엔드의 단일 계약 |
| 7 | `docs/vnext/09-quality/qa-and-acceptance-plan.md` | 기능 완료와 개발 착수 기준 |
| 8 | `docs/vnext/10-delivery/three-to-four-week-plan.md` | 주차별 결과와 의존성 |
| 9 | `docs/vnext/VERIFICATION.md` | 자동 검증 결과, 확인된 한계, 팀 승인 항목 |

## 현재 고정된 결정

- 모바일 웹·PWA로 먼저 구현한다.
- 실제 마이데이터 대신 같은 인터페이스의 어댑터와 합성데이터를 사용한다.
- MVP는 메이트 탭의 `비교 탐색`만 실제 동작한다.
- 상대의 금액을 복사하지 않고 루틴·유지기간·비율 구간만 참고한다.
- 코드가 목표값·진행률·스탯을 계산하고 AI는 후보 추천과 설명만 한다.
- 소비·저축은 정량 목표, 투자 판단·금융지식은 행동형 목표만 제공한다.
- 퀘스트 완료만으로 금융 스탯이나 레이드 진행률을 올리지 않는다.
- 포인트·쿠폰·공개 순위·실패 손실은 MVP에서 제외한다.

## 개발 착수 체크

- [ ] 화면별 정상·빈 화면·오류·오래된 데이터 상태가 승인됨
- [ ] 계산 정책과 목표 템플릿이 승인됨
- [ ] OpenAPI 예시로 mock 핵심 흐름을 재생할 수 있음
- [ ] 개인정보 공개표와 철회 흐름이 승인됨
- [ ] 핵심 E2E와 골든 계산 사례가 승인됨
- [ ] 기술 스택 ADR이 승인됨
- [ ] 3~4주 일정의 첫 주 작업이 이슈로 분해됨

## 회의 결정 기록 방식

결정은 채팅이나 PPT에만 남기지 않는다. 회의가 끝날 때 아래 형식으로 `docs/vnext/00-governance/decision-log.md`에 추가한다.

```text
결정일 / 결정 항목 / 선택 / 근거 / 영향 문서 / 재검토 조건
```

## 문서 변경 규칙

1. 구현에 영향을 주는 변경은 원본 Git 문서부터 수정한다.
2. API 변경은 OpenAPI와 예시 응답을 같은 PR에서 수정한다.
3. 계산 변경은 계산 정책, 계산 버전, 골든 테스트를 같은 PR에서 수정한다.
4. 화면 변경은 화면 명세, 분석 이벤트, E2E 인수 기준을 함께 확인한다.
5. Notion에는 문서 내용을 복제하지 않고 변경 이유와 링크만 기록한다.

## 발표·시연

- 내부 킥오프: `docs/vnext/11-sharing/FinMate_vNext_Kickoff_Draft.pptx`
- 외부 발표 초안: `docs/vnext/11-sharing/FinMate_vNext_Final_Pitch_Draft.pptx`
- 90초 시연: `docs/vnext/11-sharing/demo-storyboard-90s.md`
- 주장·근거: `docs/vnext/11-sharing/claim-evidence-matrix.md`
