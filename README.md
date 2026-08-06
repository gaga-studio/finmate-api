# FinMate API

[finmate-app](https://github.com/gaga-studio/finmate-app)이 실제로 붙는 백엔드입니다.

## 왜 다시 만드는가

이 저장소의 이전 버전(`main`)은 "vNext"라는 별도 제품 정의로 설계됐습니다.
그런데 실제로 만들어진 앱은 다른 정보 구조를 갖고 있었고, **둘은 한 번도 연결된 적이 없습니다.**

```
이전 백엔드   quests · records · goals · rewards · mate
실제 앱       diary · feed · insights · mate · missions · my
```

앱은 백엔드 없이 시드 기반 목 데이터로 돌아갔습니다. 그래서 앱의 데이터 계층
(`finmate-app/src/data/`, 14파일 2,210줄)을 **계약으로 삼아** 다시 짓습니다.
특히 `selectors.ts`는 거래 원장에서 지표를 파생하는 순수 함수 모음이라,
그대로 서버로 옮겨야 할 로직입니다.

## 현재 상태

| 단계 | 상태 |
|---|---|
| 인증 (가입·로그인·재발급·로그아웃) | ✅ |
| 원장 스키마와 적재 | 진행 중 |
| 파생 지표 API | |
| AI 그림일기 | |
| 또래 비교·피드 | |
| 미션·포인트 | |

## 실행

```bash
cp .env.example .env
docker compose up -d postgres
./gradlew bootRun
```

```bash
./gradlew test        # Testcontainers 실 Postgres
```

`FINMATE_JWT_SECRET`이 필요합니다. **비밀값은 저장소에 두지 않습니다.**

## 데이터

또래 비교가 핵심 화면이라 사람이 여러 명 필요합니다.
[finmate-data](https://github.com/gaga-studio/finmate-data)의 합성 데이터셋(2,000명)을 적재합니다.
**데이터셋은 팀원이 만든 것이며**, 이 저장소는 그것을 적재하고 정합성을 검증하는 쪽을 맡습니다.

## 팀

가가제작소 — 하나금융그룹 × 금융감독원 2026 청년 금융인재.
기획 2 / 풀스택 1 / 데이터 1.
