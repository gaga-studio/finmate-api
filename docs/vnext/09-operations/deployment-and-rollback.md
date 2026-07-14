# FinMate MVP 배포·복구 절차

## 1. 환경 경계

| 환경 | 데이터 | demo API | 외부 공개 |
| --- | --- | --- | --- |
| local | 합성 fixture | 허용 | 불가 |
| demo | 결정적 합성 사용자 | 허용 | 발표 링크만 |
| staging | 합성 seed | 차단 | 팀·테스터 제한 |
| production | 합성 seed | 차단 | 라이선스·보안 승인 후 |

실제 마이데이터, 실제 거래, LLM, 상품 가입은 모든 MVP 환경에서 비활성화한다.

## 2. 배포 전 조건

1. API와 웹 기준 커밋·OpenAPI snapshot을 동결한다.
2. QA 체크리스트의 P0·P1이 0인지 확인한다.
3. 데이터 migration을 빈 DB와 직전 staging snapshot에서 각각 검증한다.
4. production profile에서 `/api/v1/demo/**`가 404 또는 403인지 확인한다.
5. `.env.example` 이외의 비밀값이 Git·PWA bundle·로그에 없는지 검사한다.
6. Paperlogy와 캐릭터·배경 에셋의 배포 승인을 확인한다.
7. 웹 service worker의 cache version과 직전 릴리스로의 복구 방법을 기록한다.

## 3. 배포 순서

1. 데이터베이스 백업과 현재 migration version을 기록한다.
2. API artifact를 immutable version으로 배포한다.
3. health, readiness, Swagger 원본, 인증 refresh를 확인한다.
4. API smoke test: 가입 → 온보딩 → 목표 → 홈 → 메이트 → 퀘스트 → 기록.
5. OpenAPI가 고정 snapshot과 일치하는지 확인한다.
6. 웹 artifact를 immutable version으로 배포한다.
7. 360px 모바일 smoke test와 PWA 새로고침·오프라인 셸을 확인한다.
8. 30분 동안 5xx, 인증 실패, JS 오류, migration 오류를 관찰한다.

## 4. 즉시 중단·복구 조건

- 개인정보 또는 금지 필드 노출
- 금융 계산·목표 진행률 불일치
- demo API의 production 노출
- 가입·로그인·대표 흐름 차단
- migration 실패 또는 데이터 손실 징후
- 웹/API 계약 불일치로 반복 4xx·5xx 발생

복구 순서:

1. 새 트래픽을 직전 웹 artifact로 돌린다.
2. API를 직전 호환 artifact로 되돌린다.
3. migration이 additive가 아니면 쓰기를 중지하고 백업 복구를 우선한다.
4. PWA cache version을 올려 깨진 bundle 참조를 제거한다.
5. 장애 시각, 영향, 탐지, 조치, 데이터 무결성을 기록한다.

DB migration은 기본적으로 forward-fix한다. 파괴적 down migration은 자동 실행하지
않고 백업 복구와 검증된 보정 migration을 사용한다.

## 5. 배포 후 확인

- [ ] 실제 배포 SHA와 릴리스 문서 SHA가 일치
- [ ] production demo API 차단
- [ ] 합성데이터 표시와 마지막 동기화 시각 정상
- [ ] 상품 공식 링크는 새 창이며 앱 내 가입 없음
- [ ] 로그·분석 payload에 정확 금융값·토큰 없음
- [ ] 공개 철회 후 검색·추천·캐시 제거
- [ ] 모바일 하단 탭과 바텀시트 겹침 없음

배포와 복구 명령은 호스팅 공급자가 확정된 뒤 이 문서의 부록으로 추가한다. 공급자
미정 상태에서 임의의 production 명령을 기준으로 만들지 않는다.
