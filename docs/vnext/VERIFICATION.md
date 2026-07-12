# FinMate vNext 통합 검증 기록

## 판정

- 검증일: 2026-07-13
- 검증 브랜치: `codex/vnext-doc-package`
- 기술 검증: **PASS**
- 개발 착수 상태: **팀 승인 대기**

자동 검사와 독립 의미 검토에서 발견된 계약 불일치를 수정했다. 기술 검증 통과가 팀 의사결정을 대신하지는 않으므로, 팀이 [기술 스택 ADR](05-architecture/adr-001-stack-and-repository.md)과 [결정 로그](00-governance/decision-log.md)를 승인한 뒤 구현 기준으로 전환한다.

## 자동 검증 결과

| 검증 대상 | 실행 명령 | 결과 |
| --- | --- | --- |
| 필수 문서·로컬 링크·금지 용어·추적성·발표자료 | `python3 tools/scripts/validate_vnext_docs.py` | `PASS 114 checks` |
| OpenAPI·스키마·예시·도메인 교차 계약 | `uv run --with-requirements docs/vnext/06-api/requirements.txt python docs/vnext/06-api/verify_contracts.py` | 39 operations, 128 schemas, 56 examples, 53 success examples, 18 scenarios·22 envelopes, 13 endpoint 상태 검사, 목표 변경 mutation 3개, 퀘스트 완료 상태 2개·취소 1개, 공개 설정 mutation 16개·fixture mutation 4개, sync 상태 8개, range 정·부정 검사 각 3개, 27 alignments 통과 |
| Prism 목 명세 생성 | `uv run --with-requirements docs/vnext/06-api/requirements.txt python docs/vnext/06-api/build_mock_spec.py --output /tmp/finmate-vnext-openapi.mock.yaml` | `MOCK_SPEC_OK examples=68` |
| 목 명세 단위 검사 | `uv run --with-requirements docs/vnext/06-api/requirements.txt python -m unittest discover -s docs/vnext/06-api -p 'test_*.py'` | 1개 테스트 통과, 외부 참조 0개, 생성 OpenAPI 유효 |
| OpenAPI 권장 규칙 | `npx -y @redocly/cli@1.34.5 lint docs/vnext/06-api/openapi.yaml` | 오류 0건, 비차단 메타데이터 경고 2건 |
| 기존 백엔드 회귀 | `./gradlew :apps:api:test` | `BUILD SUCCESSFUL` |
| YAML 파싱과 골든 테스트 ID | Ruby YAML 파싱·중복 ID 검사 | 59개 케이스, 중복 0개 |
| Git diff 기본 위생 | `git diff --check` | 오류 0건 |

Redocly 경고는 독점 라이선스에 공개 URL이 없다는 점과 로컬 개발 서버 URL을 명시했다는 점이다. API 실행 계약에는 영향을 주지 않으며, 공개 배포 도메인과 라이선스 정책이 확정되면 메타데이터를 갱신한다.

## Mock API 실행 확인

Prism 5.14가 로컬 `externalValue`를 직접 응답하지 않는 점을 확인해, `build_mock_spec.py`로 56개 JSON 파일의 68개 참조를 inline `value`로 변환한 임시 명세를 기동했다. 그 위에서 인증·동시성·멱등성 헤더를 포함한 대표 조회와 상태 변경을 확인했다.

| 요청 | 상태 | 확인한 최상위 데이터 |
| --- | --- | --- |
| `GET /home` | 200 | `activeGoal`, `raid`, `animalStats`, `dataFreshness` |
| `GET /adventurers?domain=SAVING` | 200 | `items`, `recommendationState`, `fallbackCardKind`, `dataFreshness` |
| `GET /quests` | 200 | `items`, `page`, `dataFreshness` |
| `GET /records?from=2026-07-01&to=2026-07-30` | 200 | `summary`, `items`, `page`, `dataFreshness` |
| `PUT /me/privacy` | 200 | 공개 철회 상태, 비공개 설정, aggregate version |
| `PUT /me/privacy` (`Prefer: code=412`) | 412 | `PRECONDITION_FAILED`, 최신 `WITHDRAWN`, `privacy-v7` ETag |
| `POST /quests/{questId}/cancel` | 200 | `CANCELLED`, 취소 사유, 퀘스트 상태·버전 |

개인정보 응답은 `anonymousCardOptIn = false`, 빈 공개 필드, `WITHDRAWN` 상태만 함께 유효하도록 정확히 두 개의 조합 분기와 회귀 mutation을 추가했다. 성공·412·취소 응답은 JSON fixture와 일치하고 개인정보 ETag는 `privacy-v7`과 일치했다. 이 확인은 프론트엔드가 백엔드 구현 전에 계약 예시로 핵심 조회·변경 흐름을 개발할 수 있다는 증거다. 실제 비즈니스 로직과 영속화 동작을 검증한 결과는 아니다.

## 추적성 확인

- 핵심 요구사항 14개가 화면, OpenAPI operation, 데이터 모델과 테스트에 연결되어 있다.
- 추적표가 참조하는 테스트 ID는 모두 [59개 골든 테스트](09-quality/golden-test-cases.yaml)에 존재한다.
- 추천 모험가 표본 부족, 오래된 데이터, 부분 동기화, 목표 재설정, 목표 일시정지, 동의 철회, AI 폴백과 기록 조회 예외를 포함한다.
- OpenAPI 예시 JSON은 mock fixture와 프론트엔드 초기 fixture의 공통 원본으로 사용한다.

## 발표자료 확인

| 산출물 | 슬라이드·노트 | 파일 크기 | PDF |
| --- | --- | ---: | ---: |
| 내부 킥오프 | 13장·13개 | 411,227 bytes | 1,033,696 bytes |
| 외부 발표 초안 | 10장·10개 | 317,799 bytes | 876,142 bytes |

- 두 PPTX와 PDF는 모두 5MB 미만이다.
- PowerPoint로 내보낸 PDF에서 한국어 텍스트가 추출되는 것을 확인했다.
- [내부 킥오프 연락시트](11-sharing/rendered/kickoff-contact-sheet.png)와 [외부 발표 연락시트](11-sharing/rendered/pitch-contact-sheet.png)를 눈으로 확인했으며, 잘림이나 겹침은 발견되지 않았다.
- 외부 발표에는 실제 구현 결과와 제안·후속 검증을 구분하고, 정량 주장은 [주장·근거표](11-sharing/claim-evidence-matrix.md)에 연결했다.

## 확인된 한계

1. vNext 프론트엔드와 백엔드는 아직 구현하지 않았다. 현재 결과는 개발 전 계약 패키지다.
2. MVP 데이터는 합성 마이데이터 어댑터를 기준으로 한다. 실제 마이데이터 기관 연동은 후속 범위다.
3. 추천 모험가와 목표 계산은 합성 시나리오로 검증했다. 실제 사용자 효과나 4주 습관 유지율을 입증한 결과가 아니다.
4. 사용성 테스트 결과를 만들거나 추정하지 않았다. 핵심 흐름 프로토타입 이후 5~10명 검증을 진행해야 한다.
5. React·TypeScript·Vite PWA, Spring Boot, PostgreSQL은 승인 전 권고안이다.
6. 친구·메이트 찾기는 Phase 2이며 MVP에서 실제 동작하지 않는다.
7. 생성된 inline mock server 성공은 스키마와 예시의 실행 가능성을 뜻하며 보안·부하·영속화·운영 안정성 검증을 대신하지 않는다.

## 개발 착수 전 팀 확인

1. 프로젝트 개요와 MVP 제외 범위를 승인한다.
2. ADR의 스택·저장소 구조를 승인한다.
3. 목표 템플릿과 금융 계산 정책의 책임자를 지정한다.
4. 개인정보 공개 범위와 동의 철회 SLA를 승인한다.
5. OpenAPI를 프론트엔드·백엔드 단일 계약으로 고정한다.
6. 3~4주 일정의 담당자와 의존성을 확정한다.

위 여섯 항목을 결정 로그에 승인 상태로 반영한 뒤 구현 브랜치를 시작한다.
