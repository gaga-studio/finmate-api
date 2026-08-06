# 성능 측정

측정일 2026-08-07 · Apple M4 로컬 · Testcontainers `postgres:16-alpine` (Docker Desktop, 7.9GB 할당)
데이터 `finmate-data` 전체 2,000명 · 원장 **887,002행** (`SEED=20260713`으로 재생성)

재현:

```bash
cd finmate-data && python3 pipeline/05_generate.py
cd finmate-api
FINMATE_FULL_IMPORT=1 ./gradlew test --tests '*QueryPlan'
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

발표자료 13쪽이 스스로 *"매 렌더 원장 재계산"*이라 적어 두었고,
계획에서도 여기를 첫 성능 지점으로 잡았다. **재보니 아니었다.**

한 사람의 예산·저축·투자·소득·소비 탑5를 한 번에 만드는 요청, 200회 반복(워밍업 20회 별도):

| 기간 | p50 | p95 | max |
|---|---|---|---|
| daily | 0.71ms | 0.96ms | 1.25ms |
| weekly | 0.65ms | 0.74ms | 1.00ms |
| monthly | 0.66ms | 0.79ms | 1.02ms |

```
Aggregate  (actual time=0.012..0.012 rows=1 loops=1)
  Buffers: shared hit=4
  ->  Bitmap Heap Scan on ledger_entry  (actual time=0.005..0.007 rows=32 loops=1)
        ->  Bitmap Index Scan on idx_ledger_persona_date  (actual rows=32)
              Index Cond: ((persona_id = ...) AND (occurred_on >= '2026-07-01') AND (occurred_on <= '2026-07-13'))
Execution Time: 0.036 ms
```

**왜 안 느린가.** 원장은 89만 행이지만 **한 사람의 한 달은 32행**이다.
`(persona_id, occurred_on)` 인덱스가 그 접근 패턴을 정확히 덮어 buffer 4개만 읽는다.
"전체 데이터가 크다"와 "이 요청이 읽는 양이 크다"는 다른 말이었다.

**그래서 여기는 고치지 않는다.** 캐시나 사전 집계 테이블을 붙이면
근거 없는 복잡도만 늘어난다. 측정을 먼저 한 이유가 이것이다.

---

## 3. 또래 비교 — 여기가 비싸다

소득대별 평균 소비를 2,000명 전체에서 집계, 20회 반복:

| p50 | p95 | max |
|---|---|---|
| **34.2ms** | **52.0ms** | 52.0ms |

```
GroupAggregate  (actual time=39.213..56.904 rows=6 loops=1)
  Buffers: shared hit=12561 read=10765, temp read=311 written=311
  ->  Sort  (actual time=33.187..34.507 rows=38194 loops=3)
        Sort Key: p.income_band, p.id
        Sort Method: external merge  Disk: 2488kB
        ->  Hash Join  (actual time=0.407..21.233 rows=38194 loops=3)
              ->  Parallel Seq Scan on ledger_entry e  (actual rows=38194 loops=3)
                    Buffers: shared hit=12205 read=10765
Execution Time: 57.017 ms
```

**두 가지가 겹쳐 있다.**

1. **Parallel Seq Scan** — 조건이 `occurred_on BETWEEN`뿐인데 인덱스는
   `(persona_id, occurred_on)`이라 선행 컬럼이 없어 못 탄다. 원장 전체를 훑는다.
   디스크에서 10,765 버퍼를 읽었다.
2. **external merge Disk 2,488kB** — `count(DISTINCT p.id)` 때문에
   `(income_band, id)`로 정렬이 필요한데 `work_mem`을 넘겨 디스크로 내려갔다.

개인 조회와 접근 범위가 다르다. 한 사람의 수십 행이 아니라 인구 전체를 가로지른다.

**다음**: 쿼리부터 고친다(`DISTINCT` 제거 — persona별로 먼저 접고 그룹 평균).
그래도 남으면 인덱스를, 그래도 남으면 사전 집계를 검토한다.
싼 것부터 재고, 각 단계의 수치를 여기에 남긴다.

---

## 주장하지 않는 것

로컬 단일 머신, 컨테이너 Postgres, 단일 클라이언트 순차 호출로 잰 값이다.
동시 사용자 부하가 아니고 운영 성능도 SLO도 아니다.
`work_mem` 등 Postgres 설정은 컨테이너 기본값이며 튜닝하지 않았다.
