CREATE TABLE finmate_dataset_release (
    release_version VARCHAR(32) PRIMARY KEY,
    archive_sha256 VARCHAR(64) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    source_commit VARCHAR(40),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (archive_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE finmate_synthetic_public_profile (
    id UUID PRIMARY KEY,
    source_persona_id VARCHAR(64) NOT NULL UNIQUE,
    alias VARCHAR(120) NOT NULL,
    visible_fields TEXT NOT NULL,
    exact_values BOOLEAN NOT NULL,
    synthetic BOOLEAN NOT NULL,
    consent_state VARCHAR(32) NOT NULL,
    consent_version VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (synthetic = TRUE),
    CHECK (consent_state IN ('ACTIVE', 'OPTED_OUT'))
);

ALTER TABLE finmate_recommended_adventurer ADD COLUMN public_profile_id UUID;

INSERT INTO finmate_synthetic_public_profile (
    id, source_persona_id, alias, visible_fields, exact_values, synthetic, consent_state, consent_version, updated_at
) VALUES
    ('00000000-0000-0000-0000-000000000101', 'P-CB-001', 'Cobalt Compass',
     '["ASSETS","INCOME","SPENDING","SAVING","FINANCIAL_PRODUCTS","INVESTMENT_HOLDINGS","TRADES"]',
     TRUE, TRUE, 'ACTIVE', 'financial-disclosure-v1.0', '2026-07-13T09:00:00Z'),
    ('00000000-0000-0000-0000-000000000102', 'P-DM-001', 'Demo Lantern',
     '["ASSETS","SAVING"]', TRUE, TRUE, 'ACTIVE', 'financial-disclosure-v1.0', '2026-07-13T09:00:00Z');

UPDATE finmate_recommended_adventurer
SET public_profile_id = CASE id
    WHEN 'adv-cobalt' THEN '00000000-0000-0000-0000-000000000101'::UUID
    WHEN 'adv-demo' THEN '00000000-0000-0000-0000-000000000102'::UUID
END;

ALTER TABLE finmate_recommended_adventurer
    ALTER COLUMN public_profile_id SET NOT NULL,
    ADD CONSTRAINT finmate_recommended_adventurer_public_profile_fk
        FOREIGN KEY (public_profile_id) REFERENCES finmate_synthetic_public_profile(id);

CREATE TABLE finmate_public_financial_item (
    id UUID PRIMARY KEY,
    public_profile_id UUID NOT NULL REFERENCES finmate_synthetic_public_profile(id) ON DELETE CASCADE,
    field_name VARCHAR(32) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    category VARCHAR(64),
    amount_krw BIGINT,
    balance_krw BIGINT,
    ticker VARCHAR(32),
    allocation_bps INTEGER,
    quantity NUMERIC(20, 8),
    action VARCHAR(16),
    occurred_at TIMESTAMPTZ,
    as_of_date DATE,
    item_order INTEGER NOT NULL,
    CHECK (field_name IN ('ASSETS', 'INCOME', 'SPENDING', 'SAVING', 'FINANCIAL_PRODUCTS', 'INVESTMENT_HOLDINGS', 'TRADES')),
    CHECK (allocation_bps IS NULL OR allocation_bps BETWEEN 0 AND 10000),
    CHECK (action IS NULL OR action IN ('BUY', 'SELL')),
    UNIQUE (public_profile_id, item_order)
);

CREATE INDEX finmate_public_financial_item_profile_idx
    ON finmate_public_financial_item (public_profile_id, item_order);

INSERT INTO finmate_public_financial_item (
    id, public_profile_id, field_name, display_name, category, amount_krw, balance_krw,
    ticker, allocation_bps, quantity, action, occurred_at, as_of_date, item_order
) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101',
     'ASSETS', '여행자금 계좌', 'DEPOSIT', NULL, 5000000, NULL, NULL, NULL, NULL, NULL, '2026-07-13', 1),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000101',
     'INCOME', '월급 입금', 'SALARY', 2800000, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-09', 2),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000101',
     'SPENDING', '이번 달 생활비', 'LIVING', 42000, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-11', 3),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000101',
     'SAVING', '월급날 먼저 저축', 'AUTOMATIC_SAVING', 500000, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-10', 4),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000101',
     'FINANCIAL_PRODUCTS', '하나 여행 적금', 'SAVING_ACCOUNT', NULL, 2000000, NULL, NULL, NULL, NULL, NULL, '2026-07-13', 5),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000101',
     'INVESTMENT_HOLDINGS', 'KODEX 200', 'ETF', NULL, 1200000, '069500.KS', 2500, 8.00000000, NULL, NULL, '2026-07-13', 6),
    ('00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000101',
     'TRADES', 'KODEX 200 매수', 'ETF', 50000, NULL, '069500.KS', NULL, 1.00000000, 'BUY', '2026-07-08T02:10:00Z', NULL, 7),
    ('00000000-0000-0000-0000-000000000208', '00000000-0000-0000-0000-000000000102',
     'ASSETS', '목표 저축 계좌', 'DEPOSIT', NULL, 3200000, NULL, NULL, NULL, NULL, NULL, '2026-07-13', 1),
    ('00000000-0000-0000-0000-000000000209', '00000000-0000-0000-0000-000000000102',
     'SAVING', '주간 자동저축', 'AUTOMATIC_SAVING', 120000, NULL, NULL, NULL, NULL, NULL, NULL, '2026-07-10', 2);

INSERT INTO finmate_dataset_release (
    release_version, archive_sha256, schema_version, source_commit, period_start, period_end
) VALUES (
    'v1.0.0',
    '278226514562ec13ddb69959622bc6342fbe0b2c45e1447fa77422e3f9d3dd58',
    '1.0.0',
    '63ca3d046eba9ec510e377a28a0083233aefff61',
    '2026-01-01',
    '2026-07-13'
);
