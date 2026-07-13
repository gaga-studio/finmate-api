CREATE TABLE finmate_import_persona (
    source_persona_id VARCHAR(64) PRIMARY KEY,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    age_band VARCHAR(16) NOT NULL,
    cohort VARCHAR(32) NOT NULL,
    archetype VARCHAR(120) NOT NULL,
    occupation_group VARCHAR(120) NOT NULL,
    monthly_income_krw BIGINT NOT NULL,
    income_regularity VARCHAR(32) NOT NULL,
    target_saving_rate_bps INTEGER NOT NULL,
    target_investment_rate_bps INTEGER NOT NULL,
    risk_score INTEGER NOT NULL,
    risk_attitude VARCHAR(64) NOT NULL,
    household_type VARCHAR(64) NOT NULL,
    lifestyle_tags JSONB NOT NULL,
    financial_goal VARCHAR(160) NOT NULL,
    money_worry VARCHAR(160) NOT NULL,
    joined_at DATE NOT NULL,
    source_data_range VARCHAR(32) NOT NULL,
    source_data_as_of DATE NOT NULL,
    synthetic BOOLEAN NOT NULL,
    CHECK (target_saving_rate_bps BETWEEN 0 AND 10000),
    CHECK (target_investment_rate_bps BETWEEN 0 AND 10000),
    CHECK (synthetic = TRUE)
);

CREATE TABLE finmate_financial_activity (
    source_transaction_id VARCHAR(96) PRIMARY KEY,
    source_persona_id VARCHAR(64) NOT NULL REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    activity_type VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    category VARCHAR(80) NOT NULL,
    subcategory VARCHAR(80) NOT NULL,
    display_label VARCHAR(80) NOT NULL,
    amount_krw BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (activity_type IN ('INCOME', 'SPENDING', 'SAVING', 'INVESTMENT')),
    CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    CHECK (amount_krw >= 0)
);

CREATE INDEX finmate_financial_activity_persona_time_idx
    ON finmate_financial_activity (source_persona_id, occurred_at);

CREATE TABLE finmate_import_financial_product (
    holding_id VARCHAR(160) PRIMARY KEY,
    source_persona_id VARCHAR(64) NOT NULL REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    product_type VARCHAR(32) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    holding_status VARCHAR(16) NOT NULL,
    as_of_date DATE NOT NULL,
    synthetic BOOLEAN NOT NULL,
    CHECK (synthetic = TRUE)
);

CREATE TABLE finmate_import_investment_holding (
    holding_id VARCHAR(160) PRIMARY KEY,
    source_persona_id VARCHAR(64) NOT NULL REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    product_name VARCHAR(180) NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    purchase_amount_krw BIGINT NOT NULL,
    evaluation_amount_krw BIGINT NOT NULL,
    quantity NUMERIC(24, 8),
    currency VARCHAR(8) NOT NULL,
    as_of_date DATE NOT NULL,
    synthetic BOOLEAN NOT NULL,
    CHECK (synthetic = TRUE)
);

CREATE TABLE finmate_import_investment_trade (
    trade_id VARCHAR(64) PRIMARY KEY,
    source_persona_id VARCHAR(64) NOT NULL REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    product_name VARCHAR(180) NOT NULL,
    ticker VARCHAR(32) NOT NULL,
    action VARCHAR(8) NOT NULL,
    quantity NUMERIC(24, 8),
    gross_amount_krw BIGINT NOT NULL,
    settlement_amount_krw BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    synthetic BOOLEAN NOT NULL,
    CHECK (action IN ('BUY', 'SELL')),
    CHECK (synthetic = TRUE)
);

CREATE TABLE finmate_import_l3_record (
    table_name VARCHAR(64) NOT NULL,
    natural_key VARCHAR(64) NOT NULL,
    source_persona_id VARCHAR(64),
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    payload JSONB NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (table_name, natural_key)
);

CREATE INDEX finmate_import_l3_record_persona_idx
    ON finmate_import_l3_record (source_persona_id, table_name);
