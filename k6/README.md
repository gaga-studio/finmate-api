# 부하 테스트

지금까지의 성능 수치는 **단일 클라이언트가 순차로 호출해** 잰 값이라 처리량 지표가 아니었다.
여기서는 여러 사용자가 동시에 화면을 여는 상황을 잰다.

## 준비

```bash
# 1. 데이터셋 (없으면)
cd ../finmate-data && python3 pipeline/05_generate.py

# 2. Postgres
cd ../finmate-api && docker compose up -d postgres

# 3. 적재 + 사전 집계 + 서버
FINMATE_SEED_ON_START=true ./gradlew bootRun
```

## 실행

```bash
k6 run k6/screens.js                  # 화면 조회 부하
k6 run k6/diary-idempotency.js        # 그림일기 동시 요청 (하루 한 장인지)
```

결과는 `docs/PERF_RESULT.md`에 조건과 함께 적는다. 요약 파일에는 로그인 토큰이 들어가므로
`--summary-export`를 그대로 커밋하지 않는다.
