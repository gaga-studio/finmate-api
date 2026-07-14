# FinMate final presentation text package

이 디렉터리는 PPT 파일이 아니라 최종발표 내용을 확정하기 위한 텍스트 패키지다.
제품 정책은 여기서 새로 결정하지 않으며, 충돌이 생기면 `docs/vnext/README.md`의
규범 문서 우선순위를 따른다.

## Documents

- [`final-presentation-text-v1.md`](final-presentation-text-v1.md): 기존 발표 진단,
  본문 10장, 90초 시연 멘트, 백업 10장과 최종 검수 기준.
- [`claims-and-sources.md`](claims-and-sources.md): 발표 주장별 근거, 허용 문구,
  한계와 발표 전 갱신 항목.
- [`usability-test-template.md`](usability-test-template.md): 6명 초기 사용성 검증
  진행안, 기록표와 7번 슬라이드 작성 규칙.
- [`verification-snapshot-2026-07-13.md`](verification-snapshot-2026-07-13.md):
  발표 초안 작성 시점의 과거 API·웹·계약·E2E 실행 결과. 최신 구현 수치는
  [`VERIFICATION`](../VERIFICATION.md)을 기준으로 한다.
- [`legacy-wally-deck-transcript.md`](legacy-wally-deck-transcript.md): 기존 WALLY
  발표 20장의 텍스트 요약, 원본 SHA-256과 새 발표 이관 검토 기준.
- [`90초 시연 녹화 절차`](../09-operations/demo-recording-runbook.md): 영상 규격,
  버전 동결 manifest, 오프라인 재생과 장면별 검수 기준.

## Status labels

발표 안의 모든 주장은 다음 중 하나로 표시한다.

| 라벨 | 의미 |
| --- | --- |
| `공식공고` | 주최·운영기관이 공개한 사업 일정·평가·참가 조건 |
| `공식통계` | 정부·공공기관이 공개한 조사·통계 |
| `공식정책` | 금융·개인정보 감독기관이 공개한 정책·가이드라인 |
| `외부연구` | FinMate 외부에서 수행된 연구 결과 |
| `제품계약` | 확정된 제품 정책·계산·상태 규칙. 효과 검증을 의미하지 않음 |
| `시연fixture` | 합성데이터로 고정한 발표 사례. 실제 사용자 결과가 아님 |
| `현재구현` | 발표 직전 재검증한 저장소 상태 |
| `내부검증` | FinMate 프로토타입으로 직접 수행한 소규모 사용성 검증 |
| `공식서비스` | 공식 서비스 페이지에서 현재 확인한 기능 사실 |
| `후속계획` | 아직 구현되지 않았으며 순서와 완료 조건을 정한 다음 작업 |
| `제안` | 아직 합의되거나 검증되지 않은 파일럿·연계 방안 |

`외부연구`는 FinMate의 효과를 입증하지 않는다. `제안`은 하나금융그룹과의 승인·
연동이 완료되었다는 뜻이 아니다.
