# 성능 측정

측정일 2026-08-07 · Apple M4 로컬 · Testcontainers `postgres:16-alpine` (Docker Desktop, 7.9GB 할당)
데이터 `finmate-data` 전체 2,000명 · 원장 **887,002행** (`SEED=20260713`으로 재생성)

재현:

```bash
cd finmate-data && python3 pipeline/05_generate.py
cd finmate-api
FINMATE_FULL_IMPORT=1 ./gradlew test --tests '*QueryPlan' --tests '*Benchmark'
```

---

## 1. 적재

| 항목 | 값 |
|---|---|
| persona | 2,000명 |
| 거래 | 887,002행 |
| 소요 | 25,718ms |
| 처리율 | 34,490행/초 |

JDBC 배치(1,000행) + `ON CONFLICT DO NOTHING`.

---

## 2. 마이 탭 조회 — **예상이 틀렸다**

발표자료 13쪽이 스스로 *"매 렌더 원장 재계산"*이라 적어 두었고, 계획에서도 여기를
첫 성능 지점으로 잡았다. **재보니 아니었다.**

한 사람의 예산·저축·투자·소득·소비 탑5를 한 번에 만드는 요청, 200회(워밍업 20회 별도):

| 기간 | p50 | p95 | max |
|---|---|---|---|
| daily | 0.71ms | 0.96ms | 1.25ms |
| weekly | 0.65ms | 0.74ms | 1.00ms |
| monthly | 0.66ms | 0.79ms | 1.02ms |

```
Aggregate  (actual time=0.012..0.012 rows=1 loops=1)
  Buffers: shared hit=4
  ->  Bitmap Heap Scan on ledger_entry  (actual rows=32)
        ->  Bitmap Index Scan on idx_ledger_persona_date
Execution Time: 0.036 ms
```

**왜 안 느린가.** 원장은 89만 행이지만 **한 사람의 한 달은 32행**이다.
`(persona_id, occurred_on)` 인덱스가 그 접근 패턴을 정확히 덮어 buffer 4개만 읽는다.
"전체 데이터가 크다"와 "이 요청이 읽는 양이 크다"는 다른 말이었다.

**그래서 여기는 고치지 않았다.** 캐시나 사전 집계를 붙이면 근거 없는 복잡도만 는다.
측정을 먼저 한 이유가 이것이다.

---

## 3. 또래 비교 — 여기가 비쌌다

소득대별 평균 소비를 2,000명 전체에서 집계. 개인 조회와 접근 범위가 다르다 —
한 사람의 수십 행이 아니라 인구 전체를 가로지른다.

### 3-1. 처음 (원장 직접 집계)

p50 **32.5ms** · p95 33.0ms

```
GroupAggregate  (actual time=40.250..58.406 rows=6 loops=1)
  Buffers: shared hit=12335 read=10991, temp read=312 written=312
  ->  Sort  Sort Key: p.income_band, p.id
        Sort Method: external merge  Disk: 2496kB
        ->  Hash Join
              ->  Parallel Seq Scan on ledger_entry e  (actual rows=38194 loops=3)
Execution Time: 58.572 ms
```

두 가지가 겹쳐 있었다.

1. **Parallel Seq Scan** — 조건이 `occurred_on BETWEEN`뿐인데 인덱스는
   `(persona_id, occurred_on)`이라 선행 컬럼이 없어 못 탄다. 원장 전체를 훑고
   디스크에서 10,991 버퍼를 읽었다.
2. **external merge Disk 2,496kB** — `count(DISTINCT p.id)` 때문에
   `(income_band, id)` 정렬이 필요한데 `work_mem`을 넘겨 디스크로 내려갔다.

### 3-2. 싼 것부터 차례로 재봤다

| 방법 | p50 | 추가 저장 | 판단 |
|---|---|---|---|
| 원장 직접 집계 | 32.5ms | — | 기준 |
| ① 쿼리 수정 — persona 선집계로 `count(DISTINCT)` 제거 | 28.0ms | 0 | 디스크 정렬은 없앴지만(2,496kB → 메모리 142kB) Seq Scan이 남음 |
| ② + 커버링 인덱스 `(flow, occurred_on) INCLUDE (persona_id, amount)` | 24.0ms | **50MB** | Bitmap Index Scan을 타지만 여전히 10만 행을 집계 |
| ③ **사람×월 사전 집계 테이블** | **0.72ms** | **1.9MB** | **채택** |

**②는 채택하지 않았다.** 50MB를 쓰고 8.5ms를 벌었는데, ③은 1.9MB로 31.8ms를 번다.
사전 집계가 있으면 또래 비교가 원장을 아예 읽지 않으므로 그 인덱스가 필요 없어진다.

### 3-3. 채택안 (`persona_month`)

p50 **0.72ms** · p95 **0.95ms** — 기준 대비 **45배**

```
HashAggregate  (actual time=0.762..0.763 rows=6 loops=1)
  Buffers: shared hit=206
  ->  Bitmap Heap Scan on persona_month m  (actual rows=2000)
        ->  Bitmap Index Scan on idx_persona_month_month
  Sort Method: quicksort  Memory: 25kB
Execution Time: 0.795 ms
```

| 항목 | 값 |
|---|---|
| 행 수 | 14,000 (2,000명 × 7개월) |
| 크기 | 1,992 kB (원장 179MB의 1.1%) |
| 전체 재생성 | 401ms |
| 읽는 버퍼 | 23,326 → **206** |

**왜 이렇게 작아지는가.** 한 달치 105,484행이 접히고 나면 2,000행이다.
매 요청 접는 대신 한 번 접어 두면, 조회가 읽는 양이 그만큼 줄어든다.

**대신 치르는 값.** 원장이 바뀌면 집계도 틀어진다. 그래서
`PeerCompareIntegrationTest`가 매번 원장을 직접 세어 집계와 대조하고,
갱신 단위를 사람으로 잡아(`rebuildPersona`) 거래가 바뀐 사람만 다시 세게 했다.

### 3-4. 두 방식의 결과 대조

같은 답이 나와야 개선이라 부를 수 있다. 6개 그룹 전부 일치(평균은 반올림 차이만):

| 소득대 | 인원 (직접/집계) | 평균 소비 (직접/집계) |
|---|---|---|
| 150만원 미만 | 787 / 787 | 1,395,787.63 / 1,395,788 |
| 150~250만원 | 332 / 332 | 1,694,766.26 / 1,694,766 |
| 250~350만원 | 465 / 465 | 1,885,398.34 / 1,885,398 |
| 350~500만원 | 268 / 268 | 1,937,026.01 / 1,937,026 |
| 500~700만원 | 88 / 88 | 1,951,240.76 / 1,951,241 |
| 700만원 이상 | 60 / 60 | 2,178,327.72 / 2,178,328 |

---

## 4. 동시 사용자 부하

앞의 §2·§3은 **단일 클라이언트가 순차로** 잰 값이라 처리량 지표가 아니었다.
여기서는 50명이 동시에 앱을 여는 상황을 잰다 — 마이 탭(일·주·월) → 피드 → 미션 → 인사이트.
엔드포인트 하나만 두드리면 화면 전환에서 생기는 부하를 못 본다.

```bash
FINMATE_SEED_ON_START=true ./gradlew bootRun
k6 run k6/screens.js        # 50 VU · 30초
```

| | 값 |
|---|---|
| 요청 | **21,218** (615 req/s) |
| 실패 | **0** |
| p95 (전체) | **23.11ms** |
| p90 / median | 16.31ms / 7.03ms |
| max | 278.07ms |

화면별:

| 화면 | median | p95 |
|---|---|---|
| 마이 (예산·저축·투자·탑5) | 4.21ms | 16.42ms |
| 또래 비교 | 9.54ms | 25.39ms |
| 피드 (그룹·메이트) | 9.92ms | 27.89ms |
| 미션 | 8.53ms | 25.77ms |
| 인사이트 투영 | 5.34ms | 19.52ms |

VU마다 다른 계정으로 가입해 서로 다른 원장을 본다. 같은 계정을 나눠 쓰면
캐시가 없는데도 있는 것처럼 빨라 보인다.

§3에서 잰 0.72ms가 50 VU에서 p95 25.39ms가 됐다. 순차 호출과 동시 호출은 다른 숫자다.

## 5. 그림일기 멱등성 — HTTP로

통합 테스트에서 스레드 8개로 확인했지만 그건 한 JVM 안이다.
20명이 진짜 동시에 같은 날을 요청했을 때:

| | |
|---|---|
| 새로 만들어짐 (202) | **1** |
| 이미 있음 (200) | 19 |
| 실패 | 0 |

`(persona_id, entry_date)` 유니크 + `ON CONFLICT DO NOTHING`.
애플리케이션에서 "있는지 보고 없으면 넣는다"로 했으면 여기서 여러 건이 들어간다.

---

## 주장하지 않는 것

로컬 단일 머신, 컨테이너 Postgres에서 잰 값이다.
§4는 50 VU 30초 단일 실행이며 반복 측정·분산·신뢰구간을 계산하지 않았다.
동시 사용자 부하가 아니고 운영 성능도 SLO도 아니다.
`work_mem` 등 Postgres 설정은 컨테이너 기본값이며 튜닝하지 않았다 —
3-1의 디스크 정렬은 `work_mem`을 올려도 사라지지만, 설정으로 가리는 대신 쿼리를 고쳤다.
표본은 40~200회이며 분산·신뢰구간을 계산하지 않았다.
