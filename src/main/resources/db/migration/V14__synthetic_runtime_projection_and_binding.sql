CREATE TABLE finmate_synthetic_runtime_persona (
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    projection_version VARCHAR(32) NOT NULL DEFAULT 'synthetic-runtime-v1',
    age_band VARCHAR(16) NOT NULL,
    cohort VARCHAR(32) NOT NULL,
    occupation_group VARCHAR(120) NOT NULL,
    income_band VARCHAR(24) NOT NULL,
    spending_tendency VARCHAR(16) NOT NULL,
    saving_rate_band VARCHAR(24) NOT NULL,
    investment_tendency VARCHAR(16) NOT NULL,
    income_regularity VARCHAR(16) NOT NULL,
    household_type VARCHAR(24) NOT NULL,
    lifestyle_tags TEXT NOT NULL,
    money_worry VARCHAR(160) NOT NULL,
    peer_discovery_opt_in BOOLEAN NOT NULL,
    data_state VARCHAR(16) NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    visible_fields TEXT NOT NULL DEFAULT '[]',
    exact_values BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (source_persona_id, release_version),
    FOREIGN KEY (source_persona_id)
        REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    CHECK (income_regularity IN ('REGULAR', 'IRREGULAR', 'NONE')),
    CHECK (household_type IN ('WITH_FAMILY', 'RENT', 'DORMITORY', 'OTHER')),
    CHECK (income_band IN ('NONE', 'UNDER_200', 'FROM_200_TO_300', 'OVER_300')),
    CHECK (spending_tendency IN ('PLANNED', 'BALANCED', 'VARIABLE', 'UNKNOWN')),
    CHECK (saving_rate_band IN ('UNDER_10', 'FROM_10_TO_20', 'OVER_20', 'UNKNOWN')),
    CHECK (investment_tendency IN ('CAUTIOUS', 'BALANCED', 'LEARNING')),
    CHECK (data_state IN ('FRESH', 'INSUFFICIENT')),
    CHECK (visible_fields = '[]'),
    CHECK (exact_values = FALSE)
);

CREATE TABLE finmate_synthetic_runtime_feature_profile (
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL DEFAULT 'synthetic-runtime-v1',
    feature_month DATE NOT NULL,
    age INTEGER,
    cohort VARCHAR(32),
    income_norm_bps INTEGER,
    essential_ratio_bps INTEGER,
    consumption_rate_bps INTEGER,
    saving_rate_bps INTEGER,
    invest_rate_bps INTEGER,
    defense_score_bps INTEGER,
    saving_score_bps INTEGER,
    invest_score_bps INTEGER,
    lifestyle_cluster_id VARCHAR(64),
    PRIMARY KEY (source_persona_id, release_version),
    FOREIGN KEY (source_persona_id)
        REFERENCES finmate_import_persona(source_persona_id) ON DELETE CASCADE,
    FOREIGN KEY (source_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    CHECK (income_norm_bps IS NULL OR income_norm_bps BETWEEN 0 AND 10000),
    CHECK (essential_ratio_bps IS NULL OR essential_ratio_bps BETWEEN 0 AND 10000),
    CHECK (consumption_rate_bps IS NULL OR consumption_rate_bps BETWEEN 0 AND 10000),
    CHECK (saving_rate_bps IS NULL OR saving_rate_bps BETWEEN 0 AND 10000),
    CHECK (invest_rate_bps IS NULL OR invest_rate_bps BETWEEN 0 AND 10000),
    CHECK (defense_score_bps IS NULL OR defense_score_bps BETWEEN 0 AND 10000),
    CHECK (saving_score_bps IS NULL OR saving_score_bps BETWEEN 0 AND 10000),
    CHECK (invest_score_bps IS NULL OR invest_score_bps BETWEEN 0 AND 10000)
);

CREATE TABLE finmate_synthetic_runtime_routine (
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL DEFAULT 'synthetic-runtime-v1',
    source_routine VARCHAR(120) NOT NULL,
    domain VARCHAR(16) NOT NULL,
    frequency VARCHAR(64),
    ratio_bps INTEGER,
    maintained_months INTEGER,
    PRIMARY KEY (source_persona_id, release_version, source_routine),
    FOREIGN KEY (source_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    CHECK (domain IN ('SAVING', 'SPENDING')),
    CHECK (ratio_bps IS NULL OR ratio_bps BETWEEN 0 AND 10000),
    CHECK (maintained_months IS NULL OR maintained_months >= 0)
);

CREATE TABLE finmate_user_synthetic_persona_binding (
    user_id UUID PRIMARY KEY REFERENCES finmate_user(id) ON DELETE CASCADE,
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL DEFAULT 'synthetic-runtime-v1',
    bound_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (source_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version)
);

ALTER TABLE finmate_onboarding_state
    ADD COLUMN age_band VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN occupation_group VARCHAR(120) NOT NULL DEFAULT 'UNKNOWN';
