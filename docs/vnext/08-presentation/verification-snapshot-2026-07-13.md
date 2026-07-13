# FinMate 발표 구현 검증 스냅샷 · 2026-07-13

> 이 문서는 발표 8장의 초안 수치를 재현하기 위한 일시적 스냅샷이다. 최종 발표
> 48시간 전에 같은 검증을 다시 실행하고 최신 스냅샷으로 교체한다.

## 1. 검증 기준

| 저장소 | 커밋 | 상태 |
| --- | --- | --- |
| `gaga-studio/finmate-api` | `b3b4853` | 사용자 작업인 untracked `.DS_Store` 외 코드 변경 없음 |
| `gaga-studio/finmate-web` | `a89ec7a` | 검증 시작 전·종료 후 clean |

발표 문서 변경은 별도 브랜치 `codex/final-presentation-text-v1`의 격리 worktree에서
작성했으며 제품 런타임 코드를 수정하지 않았다.

## 2. 통과 결과

### API

```bash
./gradlew test
```

- 결과: `BUILD SUCCESSFUL`
- JUnit XML 기준: `89 tests`

```bash
.venv/bin/python docs/vnext/06-api/verify_contracts.py
```

- 결과: `CONTRACT_VERIFICATION_OK`
- OpenAPI operation `35`, schema `68`, example `28`
- structural checks `30`, auth checks `14`, goal checks `5`

```bash
.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py
```

- 결과: `8 tests OK`

### Web

```bash
npm test
```

- 결과: `2 test files`, `18 tests passed`

```bash
npm run lint
npm run build
```

- 결과: 둘 다 통과

```bash
npm run test:e2e
```

- 결과: Mock API 기반 대표 모바일 흐름 `1 passed`

### 발표 텍스트 패키지

- Markdown 문서: `6개`
- 본문 슬라이드: `10장`
- 발표 시간 합계: `410초`
- 내부 문서 링크: `26개` 경로 확인
- 기존 WALLY 발표: 본문·백업 `20장` 이관표와 원본 SHA-256 확인
- 사용성 결과 부재 시: `후속계획` 대체 7장으로 10장·410초 유지
- 주장 라벨: README에 정의된 `11개` 라벨만 사용

## 3. 통과하지 않은 통합 검증

```bash
npm run test:e2e:api
```

- 결과: 미통과
- 실패 단계: 실제 브라우저 E2E 실행 전 OpenAPI 생성 클라이언트 diff 검사
- 직접 원인: API의 확장 OpenAPI에는 목표 후확정, 분야별 리포트, 확장 메이트,
  퀘스트 수락, 발판 여정 계약이 추가됐지만 웹의 커밋된 생성 클라이언트는 이전
  계약을 유지하고 있다.
- 의미: API와 Mock 웹의 계층별 흐름은 검증됐지만, 확장 계약 기준의 실제 API
  수직 흐름이 통합됐다고 주장할 수 없다.
- 후속 조건: OpenAPI 클라이언트를 갱신한 뒤 화면·Mock·실제 API를 같은 계약에
  정렬하고 `test:e2e:api`를 다시 통과시켜야 한다.

실패한 명령이 만든 `src/api/generated.ts`와 `src/api/openapi.snapshot.yaml` 변경은
검증 전 clean 상태로 복원했다. 제품 코드에는 변경을 남기지 않았다.

## 4. 발표 8장에서 허용되는 현재 문장

> 2026년 7월 13일 기준 API 테스트 89개, 웹 테스트 18개와 Mock 대표 E2E
> 1개, lint와 build가 통과했습니다. 실제 API E2E는 OpenAPI와 웹 생성
> 클라이언트가 정렬되지 않아 아직 통과하지 못했습니다.

금지 문장:

- `신규 계약의 전체 사용자 흐름이 실제 API로 통합됐다.`
- `확장 IA의 모든 화면이 구현됐다.`
- `실제 마이데이터와 연결됐다.`

## 5. 발표 48시간 전 재검증

- [ ] API와 웹의 발표 대상 커밋 SHA를 기록했다.
- [ ] API 89개·웹 18개를 그대로 복사하지 않고 최신 결과를 집계했다.
- [ ] 계약 검증과 Mock spec 테스트를 다시 실행했다.
- [ ] Mock E2E와 실제 API E2E를 각각 실행했다.
- [ ] 실패 결과도 [`주장·출처표`](claims-and-sources.md)에 반영했다.
- [ ] 생성·빌드 산출물을 제외한 두 저장소가 clean인지 확인했다.
