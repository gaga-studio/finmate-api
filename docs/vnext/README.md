# FinMate vNext 개발 준비 패키지

이 폴더는 FinMate 전면 재개발의 단일 기준 문서다. 기존 제품 문서와 코드는 삭제하지 않으며, 검증 근거와 회귀 테스트 자료로만 사용한다.

## 제품 기준

> 추천 모험가에게서 발견한 금융 루틴을 내 상황에 맞는 목표로 바꾸고, 실제 금융행동의 변화를 자동 레이드와 기록으로 보여준다.

대표 흐름은 `추천 모험가 → 루틴 선택 → 목표 확정 → 자동 레이드 → 퀘스트 → 기록`이다.

## 문서 운영 원칙

- Git의 Markdown, OpenAPI, YAML, JSON, PPTX가 원본이다.
- Notion은 링크, 진행 상태, 회의 결정만 제공한다.
- 모든 요구사항은 `기획 → 화면 → API → 데이터 → 테스트`로 추적한다.
- 확정되지 않은 항목은 문서 본문에 추측으로 섞지 않고 결정 로그에 기록한다.
- 기존 구현은 새 계약과 핵심 E2E가 통과할 때까지 삭제하지 않는다.

## 패키지 지도

| 폴더 | 목적 |
| --- | --- |
| `00-governance` | 레거시 분류, 결정 로그, 요구사항 추적 |
| `01-product` | 프로젝트 개요, PRD, 용어 사전 |
| `02-ux` | IA, 사용자 흐름, 화면·디자인 명세 |
| `03-domain` | 도메인, 계산, 상태 전이, 목표 템플릿 |
| `04-data` | ERD, 필드 사전, 마이데이터 어댑터, 합성 시나리오 |
| `05-architecture` | 시스템 구조, 기술 ADR, 비기능 요구사항 |
| `06-api` | `/api/v1` OpenAPI, 규칙, 예시 응답 |
| `07-ai-safety` | AI 책임, 개인정보, 동의, 위협 모델 |
| `08-analytics` | 이벤트와 KPI |
| `09-quality` | QA, 인수 기준, 골든 테스트 |
| `10-delivery` | 개발 규칙, 일정, 리스크 |
| `11-sharing` | Notion 포털, 발표, 시연 스토리보드 |

통합 검증 명령과 결과, 아직 검증하지 않은 범위는 [VERIFICATION.md](VERIFICATION.md)에 기록한다.

## Mock API 실행

OpenAPI의 56개 JSON 파일이 예시 원본이다. Prism 5.14는 로컬 `externalValue`를 직접 응답으로 사용하지 않으므로 원본 명세를 바로 실행하지 않고, 먼저 68개 재사용 참조를 인라인한 임시 명세를 만든다.

```bash
uv run --with-requirements docs/vnext/06-api/requirements.txt \
  python docs/vnext/06-api/build_mock_spec.py \
  --output /tmp/finmate-vnext-openapi.mock.yaml

npx -y @stoplight/prism-cli@5.14.2 mock \
  /tmp/finmate-vnext-openapi.mock.yaml \
  --host 127.0.0.1 --port 4011
```

프론트엔드 mock base URL은 `http://127.0.0.1:4011`로 덮어쓴다. 생성 파일은 임시 산출물이며 Git에 올리지 않고, 예시를 바꿀 때는 `06-api/examples/*.json`만 수정한다.

## 문서 상태

- `Draft`: 작성 중이며 구현 기준으로 사용할 수 없음
- `Review`: 팀 검토 가능
- `Approved`: 구현 기준
- `Superseded`: 새 버전으로 대체됨

## 개발 착수 게이트

다음 조건을 모두 만족해야 구현을 시작한다.

1. 핵심 흐름의 화면, API 예시, 데이터와 테스트가 연결되어 있다.
2. 목표·레이드·퀘스트·동기화의 상태와 계산 책임이 확정되어 있다.
3. 프론트엔드는 mock API만으로 핵심 흐름을 구현할 수 있다.
4. 백엔드는 OpenAPI와 계산 정책만으로 응답을 구현할 수 있다.
5. 데이터 부족·오래됨·동의 철회·목표 중지 시나리오가 정의되어 있다.
6. 발표의 모든 수치 주장이 근거표와 연결되어 있다.
