CREATE TABLE finmate_synthetic_runtime_daily_budget (
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL,
    activity_date DATE NOT NULL,
    cumulative_spend_krw BIGINT NOT NULL,
    daily_budget_krw BIGINT NOT NULL,
    PRIMARY KEY (source_persona_id, release_version, activity_date),
    FOREIGN KEY (source_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    CHECK (cumulative_spend_krw >= 0),
    CHECK (daily_budget_krw >= 0)
);

CREATE INDEX finmate_runtime_budget_month_idx
    ON finmate_synthetic_runtime_daily_budget (source_persona_id, release_version, activity_date);

CREATE TABLE finmate_synthetic_runtime_behavior_profile (
    source_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL,
    risk_profile_checked BOOLEAN NOT NULL,
    diversification_checked BOOLEAN NOT NULL,
    investment_learning_completed BOOLEAN NOT NULL,
    investment_judgment_bps INTEGER NOT NULL,
    quest_xp INTEGER NOT NULL,
    last_evidence_date DATE,
    PRIMARY KEY (source_persona_id, release_version),
    FOREIGN KEY (source_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    CHECK (investment_judgment_bps BETWEEN 0 AND 10000),
    CHECK (quest_xp >= 0)
);
