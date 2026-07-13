# FinMate 발표 구현 검증 스냅샷 · 2026-07-13

> 이 문서는 발표 8장의 초안 수치를 재현하기 위한 일시적 스냅샷이다. 최종 발표
> 48시간 전에 같은 검증을 다시 실행하고 최신 스냅샷으로 교체한다.
> 2026-07-14 이후의 현재 수치는 [`VERIFICATION`](../VERIFICATION.md)을 기준으로 하며,
> 이 문서의 importer 16개 결과는 과거 실행 기록으로만 남긴다.

## 1. 검증 기준

| 저장소 | 커밋 | 상태 |
| --- | --- | --- |
| `gaga-studio/finmate-api` | 기준 `3b16ffe`, `codex/finmate-data-integration` 작업 중 | 합성데이터·공개정책 구현 검증, 커밋 전 |
| `gaga-studio/finmate-web` | 이 변경에서 미검증 | API 작업 범위에 포함하지 않음 |

구현과 문서는 격리 worktree에서 작성했으며 기존 저장소의 사용자 변경은 수정하지
않았다. 발표 48시간 전에는 최종 커밋 SHA로 이 표를 다시 고정해야 한다.

## 2. 통과 결과

### API

```bash
./gradlew test
```

- 결과: `BUILD SUCCESSFUL`
- JUnit XML 기준: `105 tests`, 실패 `0`

```bash
.venv/bin/python docs/vnext/06-api/verify_contracts.py
```

- 결과: `CONTRACT_VERIFICATION_OK`
- OpenAPI operation `44`, schema `86`, example `36`
- structural checks `39`, auth checks `14`, goal checks `5`

```bash
.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py
```

- 결과: `10 tests OK`

```bash
python3 -m unittest discover -s tools/finmate_data_import/tests -v
```

- 결과: `16 tests OK`
- 실제 `finmate-data v1.0.0` SHA-256 일치
- 변환: 합성 사용자 `2,000명`, 금융활동 `887,002건`
- L3 분류: 런타임 `16개·845,202행`, 골든 `5개·444,000행`, 제외 `10개`
- 격리 PostgreSQL 표본을 두 번 적재해 행 수 불변 확인

### Web · 이전 스냅샷 참고값

```bash
npm test
```

- 이전 결과: `2 test files`, `18 tests passed`

```bash
npm run lint
npm run build
```

- 이전 결과: 둘 다 통과

```bash
npm run test:e2e
```

- 이전 결과: Mock API 기반 대표 모바일 흐름 `1 passed`

위 웹 결과는 이번 API 브랜치에서 재실행하지 않았다. 최종 발표 수치로 사용하려면
웹의 최종 디자인 인수 커밋에서 다시 검증해야 한다.

### 발표 텍스트 패키지

- Markdown 문서: `6개`
- 본문 슬라이드: `10장`
- 발표 시간 합계: `410초`
- 내부 문서 링크: `26개` 경로 확인
- 기존 WALLY 발표: 본문·백업 `20장` 이관표와 원본 SHA-256 확인
- 사용성 결과 부재 시: `후속계획` 대체 7장으로 10장·410초 유지
- 주장 라벨: README에 정의된 `11개` 라벨만 사용

## 3. 아직 주장하지 않는 통합 범위

```bash
npm run test:e2e:api
```

- 이번 API 브랜치에서는 실행하지 않았다.
- 확장 OpenAPI에 맞춰 웹 생성 클라이언트·fixture·화면을 다시 생성한 뒤 수행한다.
- 실제 API 수직 흐름은 이 명령이 최종 웹 커밋에서 통과하기 전까지 구현 완료로
  주장하지 않는다.

실제 `finmate-data` 전체 export는 변환했지만 운영 PostgreSQL에 일괄 적재하지 않았다.
PostgreSQL 재적재 검증은 관계가 연결된 표본으로 수행했다.

## 4. 발표 8장에서 허용되는 현재 문장

> 2026년 7월 13일 기준 API 테스트 105개와 importer 테스트 16개가 통과했습니다.
> 잠긴 합성데이터 2,000명의 금융활동 887,002건을 허용 필드로 변환했고, 정확값
> 공개 동의·철회와 읽기 전용 소셜 경계를 API에서 검증했습니다. 최종 웹과 실제 API
> E2E는 디자인 인수 후 다시 연결해야 합니다.

금지 문장:

- `신규 계약의 전체 사용자 흐름이 실제 API로 통합됐다.`
- `확장 IA의 모든 화면이 구현됐다.`
- `실제 마이데이터와 연결됐다.`
- `2,000명의 실제 고객 데이터를 검증했다.`
- `전체 합성데이터를 운영 DB에 적재했다.`

## 5. 발표 48시간 전 재검증

- [ ] API와 웹의 발표 대상 커밋 SHA를 기록했다.
- [ ] API 105개·웹 이전 18개를 그대로 복사하지 않고 최종 커밋에서 최신 결과를 집계했다.
- [ ] 계약 검증과 Mock spec 테스트를 다시 실행했다.
- [ ] Mock E2E와 실제 API E2E를 각각 실행했다.
- [ ] 실패 결과도 [`주장·출처표`](claims-and-sources.md)에 반영했다.
- [ ] 생성·빌드 산출물을 제외한 두 저장소가 clean인지 확인했다.
