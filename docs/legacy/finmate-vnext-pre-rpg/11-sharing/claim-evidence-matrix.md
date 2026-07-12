# FinMate vNext 주장·근거표

> 상태: Review  
> 기준일: 2026-07-12

| ID | 발표 주장 | 허용 표현 | 근거 | 제한 |
| --- | --- | --- | --- | --- |
| E-01 | 20대의 재무행동 취약성 | 20대의 재무상황 점검 `33.2`, 장기 재무목표 설정 `36.1`; 전체는 각각 `43.4`, `42.5` | [한국은행·금융감독원 2024 전국민 금융이해력 조사](https://www.bok.or.kr/portal/bbs/B0000502/view.do?depth=201265&menuNo=201265&nttId=10091152&programType=newsData&relate=Y) | 20대 표본 수는 확인되지 않아 적지 않음 |
| E-02 | 게임형 금융교육의 가능성 | 4개국 학생 2,220명 RCT에서 금융이해력 `+0.313 SD` | [Cannistrà et al., 2024](https://www.sciencedirect.com/science/article/pii/S0147596724000441) | FinMate·성인 행동 효과로 확대 금지 |
| E-03 | 협력적 저축 장치의 가능성 | 칠레 고객 `2,687명` 실험에서 그룹 처리의 입금 빈도 `3.7배` | [Kast·Meier·Pomeranz](https://business.columbia.edu/faculty/research/saving-more-groups-field-experimental-evidence-chile) | 서비스 예상 효과로 사용 금지 |
| E-04 | 단순 상향 비교의 위험 | 일부 저축이 낮은 직원은 또래 정보 이후 저축이 감소 | [Beshears et al., NBER w17345](https://www.nber.org/papers/w17345) | 유사 출발점·순위 제거의 설계 근거 |
| E-05 | 현재 합성데이터 import | 합성 사용자 `199명`, 거래 `23,041건`, 관계 `995개` | `README.md`, restore 검증 스크립트 | 실제 고객·운영 DB로 표현 금지 |
| E-06 | 연구용 합성 데이터 | 합성 사용자 `199명`, 거래 `171,770건`, 6개월 | 기존 산출보고서·intelligence 테스트 | 앱 적재 건수와 혼용 금지 |
| E-07 | AI 평가 준비도 | 라우팅·수치 무결성·경계 거부를 점검하는 `20문항` 골든셋과 평가 러너 보유 | `apps/intelligence/tests/golden_questions.yaml`, `apps/intelligence/intelligence/agent/eval.py` | 실행 결과 파일이 없으므로 정확도·통과 수치 주장 금지 |
| E-08 | 마이데이터 설계 방향 | 동의·본인정보 관리·전송 상태를 제품 상태로 다룸 | [금융위원회 마이데이터 2.0](https://fsc.go.kr/po010101/84780) | MVP는 합성 어댑터이며 실제 연동 아님 |
| E-09 | 금융 AI 책임 | 계산은 코드, AI는 제한된 후보 추천·설명 | [금융위원회 금융분야 AI 가이드라인](https://www.fsc.go.kr/no010101/87142) | 준수 인증으로 표현 금지 |
| E-10 | vNext 목표 생성 | 검수된 템플릿과 사용자 확정으로 목표를 생성 | vNext 목표·계산·API 명세 | 구현·사용자 효과가 아니라 설계 계약 |
| E-11 | 파일럿 제안 | `100명·4주`로 목표 확정과 첫 행동 전환을 측정 | 팀 제안 | 실행 결과로 표현 금지 |

## 제품 내부 근거

- 기존 구현: `README.md`, `contracts/openapi/finmate-product-mvp.yaml`, 백엔드 테스트
- 새 제품 계약: `docs/vnext/01-product/`, `03-domain/`, `06-api/`, `09-quality/`
- 기존 대형 기획 입력: `docs/design/_codex-current-chat/11-auto-raid-rpg-planning-v3/`

## 발표 검수 규칙

1. 수치가 있는 슬라이드는 근거 ID를 발표자 노트와 하단 출처에 넣는다.
2. 연구 결과는 `설계 근거`, 내부 테스트는 `기술 검증`, 파일럿은 `계획`으로 구분한다.
3. 합성데이터는 모든 첫 언급에 `합성`을 붙인다.
4. 프로토타입 화면은 실제 API 연동 화면처럼 설명하지 않는다.
