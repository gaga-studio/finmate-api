-- 사람×월 사전 집계.
--
-- 또래 비교는 소득대가 같은 사람 전부를 가로질러 센다. 원장에서 직접 하면 한 달치가
-- 105,484행이고 p50 21ms가 나왔다. 그런데 그 10만 행이 접히고 나면 2,000행뿐이다.
-- 미리 접어 두면 조회가 그 2,000행만 읽는다 — p50 1.18ms, 17.7배.
--
-- 인덱스로도 재 봤다. (flow, occurred_on) INCLUDE (persona_id, amount) 커버링 인덱스는
-- 50MB를 쓰고 p50 27.8 → 24.0ms였다. 이 테이블은 1.9MB로 21 → 1.2ms다.
-- 그래서 인덱스는 넣지 않는다. 측정 근거는 docs/PERF_RESULT.md.
--
-- 개인 화면(마이 탭)은 여전히 원장을 직접 읽는다. 거기는 한 사람의 한 달이 32행이라
-- 이미 p95 0.96ms이고, 사전 집계를 태우면 복잡도만 는다.

CREATE TABLE persona_month (
    persona_id UUID   NOT NULL REFERENCES persona (id) ON DELETE CASCADE,
    -- 해당 월의 1일
    month      DATE   NOT NULL,
    spend      BIGINT NOT NULL,
    saved      BIGINT NOT NULL,
    invested   BIGINT NOT NULL,
    earned     BIGINT NOT NULL,
    PRIMARY KEY (persona_id, month)
);

-- "이번 달, 소득대별로" 가 가장 잦은 질문이다. 월로 먼저 좁힌다.
CREATE INDEX idx_persona_month_month ON persona_month (month);
