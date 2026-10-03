# FinMate API

> 금융이 막막한 20대의 첫 금융 온보딩 서비스, FinMate의 백엔드

FinMate는 또래의 금융 생활을 구경하고, 자신의 소비를 돌아보고, 작은 미션으로 금융관리를 시작하도록 돕는 모바일 웹 서비스입니다. 하나금융그룹 × 금융감독원 **2026 청년 금융인재** 프로젝트에서 가가제작소 팀으로 만들었습니다.

이 저장소는 FinMate 앱이 사용할 금융 데이터와 조회 API를 담당합니다.

[FinMate 앱](https://github.com/gaga-studio/finmate-app) · [데이터 프로젝트](https://github.com/gaga-studio/finmate-data) · [팀 발표자료](https://github.com/gaga-studio/finmate-app/blob/main/docs/FinMate-발표자료.pptx)

## 프로젝트가 다루는 문제

금융 정보를 많이 보여주는 것만으로는 첫 행동을 만들기 어렵다고 보았습니다. FinMate는 내 소비·저축·투자를 친근한 카드로 보여주고, 나와 비슷한 사람의 스토리와 작은 미션을 연결합니다.

백엔드는 그중 **내 금융 생활을 같은 기준으로 읽고 비교할 수 있는 데이터**를 제공합니다. 누구의 자료인지, 어느 기간의 자료인지, 거래가 없었던 것인지 아직 자료가 없는 것인지 구분해 앱에 전달합니다.

## 서비스 화면

![FinMate의 마이 화면과 하단 탭](docs/assets/screens/demo-desktop.png)

고정 데이터를 사용하는 FinMate 앱의 시연 화면입니다. 서버 모드에서는 기존 예산 카드·소비 탑 5, 피드의 더보기, 기록의 월별 요약·날짜별 거래에 이 API의 합성 원장이 연결됩니다.

<details>
<summary>모바일 화면</summary>

<img src="docs/assets/screens/demo-mobile.png" alt="FinMate 마이 모바일 화면" width="320" />

</details>

## 주요 기능

| 기능 | 제공하는 내용 |
|---|---|
| 내 소비 요약 | 기준일의 일간·주간·월간 예산, 소비, 저축, 투자, 소득을 조회합니다. |
| 거래 내역 | 기간별 거래의 날짜·가맹점·분류·금액을 페이지 단위로 조회합니다. |
| 또래 비교 | 같은 소득대의 월평균 소비와 내 소비를 같은 기간 기준으로 비교합니다. |
| 계정과 내 데이터 | 로그인한 사용자의 자료를 연결하고, 다른 사람의 원장에 접근하지 않도록 구분합니다. |
| 데모 데이터 | 실제 은행 연동 없이 합성 인물과 원장으로 서비스를 실행할 수 있습니다. |

앱의 기본 흐름은 마이에서 내 생활을 확인하고, 피드에서 또래를 찾고, 분석·미션·기록으로 이어집니다. 그림일기·AI 코치·추천 미션 등은 준비된 팀 시연 콘텐츠를 사용합니다.

## 데이터와 현재 범위

실제 은행 계좌나 실사용자의 금융 정보를 사용하지 않습니다. 로컬 데모는 가상 인물 24명과 2026년 1~7월의 합성 거래를 사용합니다.

자료가 준비된 기간의 무거래자는 소비 0원으로 보지만, 미적재 기간은 '자료 없음'입니다. 또래 비교는 해당 월 전체 자료가 준비된 같은 소득대의 사람을 기준으로 합니다. 금융 상품의 적합성이나 실제 투자 성과를 판단하는 서비스는 아닙니다.

## 로컬에서 실행하기

Docker가 실행 중인 환경에서 시작합니다.

```bash
docker compose -p finmate-rebuild -f compose.demo.yml up -d --build
curl http://localhost:18081/actuator/health
```

API는 `http://localhost:18081`입니다. 첫 실행에서 저장소의 합성 데이터를 적재합니다. 화면은 [finmate-app의 실행 안내](https://github.com/gaga-studio/finmate-app#로컬에서-실행하기)를 따릅니다.

```bash
# 서버 검증: Java 21·Docker 필요
./gradlew test
# 종료하되 로컬 DB 데이터 보존
docker compose -p finmate-rebuild -f compose.demo.yml stop
```

Compose의 데모 계정과 설정은 로컬 체험용입니다. 공개 배포에는 별도의 환경 설정이 필요합니다.

## 사용 기술

Java 21 · Spring Boot · Spring MVC · JPA · PostgreSQL · Flyway · Docker Compose.

## 프로젝트 자료와 역할

- [앱과 시연 흐름](https://github.com/gaga-studio/finmate-app)
- [데이터 EDA·합성 원장 생성](https://github.com/gaga-studio/finmate-data)
- [API 코드 읽기와 실행 구조](docs/SERVICE_GUIDE.md) · [검증 기록](docs/VERIFICATION.md)
- [조회 실험의 조건과 결과](docs/PERF_RESULT.md) · [화면 이미지 출처](docs/assets/README.md)

기획·리서치와 데이터 EDA는 팀 작업입니다. 이후 개인 보강에서는 소비 조회와 집계의 정확성, 재현 가능한 실행을 다뤘습니다. 개인 보강의 구현·검증에는 AI를 활용했으며, 상세 기록은 개발 문서에 남겼습니다.
