-- 원장과 합성 인구.
--
-- 이 서비스의 핵심 화면은 "나와 비슷한 또래는 어떻게 쓰는가"다. 그러려면 사람이 여러 명 필요한데,
-- 그 여러 명이 전부 로그인하는 계정일 필요는 없다. 그래서 둘을 나눈다.
--
--   finmate_user  로그인하는 사람 (V1)
--   persona       합성 인구 2,000명 — 비교의 모집단
--   ledger_entry  persona의 거래 원장
--
-- 2,000명을 finmate_user로 만들면 쓰지도 않을 비밀번호 해시와 이메일이 2,000개 생기고,
-- 인증 테이블이 인구 통계 조회에 끌려 들어온다. 발표자료 13쪽의 "개인 원장 / 인구 데이터"
-- 구분과도 같은 선이다.
--
-- 데이터 출처: gaga-studio/finmate-data (팀원 제작 합성 데이터셋, SEED=20260713 결정적 생성).
-- 실존 인물의 정보가 아니다.

CREATE TABLE persona (
    id                     UUID PRIMARY KEY,
    -- 데이터셋의 P0001 형식. 재적재 시 같은 사람을 알아보는 자연키다.
    external_id            VARCHAR(16)  NOT NULL UNIQUE,
    display_name           VARCHAR(40)  NOT NULL,

    age                    SMALLINT     NOT NULL,
    cohort                 VARCHAR(8)   NOT NULL,
    job                    VARCHAR(40)  NOT NULL,
    archetype              VARCHAR(40)  NOT NULL,
    region                 VARCHAR(40)  NOT NULL,
    household_type         VARCHAR(40)  NOT NULL,
    household_size         SMALLINT     NOT NULL,

    monthly_income         BIGINT       NOT NULL,
    income_band            VARCHAR(40)  NOT NULL,
    income_regularity      VARCHAR(16)  NOT NULL,
    target_monthly_spend   BIGINT       NOT NULL,
    target_saving_rate     NUMERIC(5,4) NOT NULL,
    target_investment_rate NUMERIC(5,4) NOT NULL,
    invest_participation   BOOLEAN      NOT NULL,
    risk_score             SMALLINT     NOT NULL,
    risk_attitude          VARCHAR(24)  NOT NULL,

    -- 데이터가 언제부터 언제까지인지. 화면이 "이번 달"을 어디로 잡을지가 여기서 나온다.
    data_from              DATE         NOT NULL,
    data_to                DATE         NOT NULL
);

-- 그룹 찾기는 소득대·지역·연령으로 모집단을 좁힌다.
CREATE INDEX idx_persona_income_band ON persona (income_band);
CREATE INDEX idx_persona_cohort_age ON persona (cohort, age);

-- 로그인한 사람을 합성 인구 한 명에 붙인다. 데모에서 "내 데이터"가 있어야 화면이 돈다.
ALTER TABLE finmate_user ADD COLUMN persona_id UUID REFERENCES persona (id);

CREATE TABLE ledger_entry (
    id             BIGSERIAL PRIMARY KEY,
    persona_id     UUID        NOT NULL REFERENCES persona (id) ON DELETE CASCADE,
    -- P0001-T0441. (persona, external) 유니크라 적재를 몇 번 돌려도 중복이 쌓이지 않는다.
    external_id    VARCHAR(32) NOT NULL,

    occurred_at    TIMESTAMP   NOT NULL,
    -- 일·주·월 집계가 이 서비스의 거의 전부다. 매번 date(occurred_at)를 계산하면
    -- 인덱스를 못 타므로 아예 컬럼으로 굳힌다.
    occurred_on    DATE        NOT NULL GENERATED ALWAYS AS (occurred_at::date) STORED,

    merchant       VARCHAR(120) NOT NULL,
    -- 원 단위 정수. 지출 음수, 수입 양수. 화폐는 전량 KRW라 컬럼을 두지 않는다.
    amount         BIGINT      NOT NULL,

    -- 앱의 카테고리(식비·카페·교통…). 매핑 규칙은 LedgerCategory에 있다.
    category       VARCHAR(16) NOT NULL,
    -- 소비/저축/소득/투자. 데이터셋이 이미 분류해 둔 값이고,
    -- 그림일기의 "그날의 주인공" 판정이 이걸 그대로 쓴다.
    flow           VARCHAR(8)  NOT NULL,

    -- 원본 분류를 남긴다. 화면의 숫자 하나를 데이터셋 생성 규칙까지 되짚을 수 있어야 한다.
    major          VARCHAR(16) NOT NULL,
    minor          VARCHAR(24) NOT NULL,
    rule_id        VARCHAR(64),

    payment_method VARCHAR(40),
    memo           VARCHAR(200),

    CONSTRAINT uq_ledger_persona_external UNIQUE (persona_id, external_id),
    CONSTRAINT ck_ledger_flow CHECK (flow IN ('소비', '저축', '소득', '투자'))
);

-- 거의 모든 조회가 "이 사람의 이 기간"이다.
CREATE INDEX idx_ledger_persona_date ON ledger_entry (persona_id, occurred_on);
