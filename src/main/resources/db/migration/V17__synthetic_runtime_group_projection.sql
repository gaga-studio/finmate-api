CREATE TABLE finmate_synthetic_runtime_group_profile (
    source_group_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL REFERENCES finmate_dataset_release(release_version),
    projection_version VARCHAR(32) NOT NULL DEFAULT 'synthetic-runtime-v1',
    description VARCHAR(240) NOT NULL,
    member_count INTEGER NOT NULL,
    average_age NUMERIC(4, 1),
    average_spending_defense_bps INTEGER NOT NULL,
    average_saving_hp_bps INTEGER NOT NULL,
    average_investment_judgment_bps INTEGER NOT NULL,
    average_consumption_rate_bps INTEGER NOT NULL,
    average_saving_rate_bps INTEGER NOT NULL,
    data_as_of DATE NOT NULL,
    PRIMARY KEY (source_group_id, release_version),
    CHECK (member_count >= 30),
    CHECK (average_spending_defense_bps BETWEEN 0 AND 10000),
    CHECK (average_saving_hp_bps BETWEEN 0 AND 10000),
    CHECK (average_investment_judgment_bps BETWEEN 0 AND 10000),
    CHECK (average_consumption_rate_bps BETWEEN 0 AND 10000),
    CHECK (average_saving_rate_bps BETWEEN 0 AND 10000)
);

CREATE INDEX finmate_runtime_group_data_as_of_idx
    ON finmate_synthetic_runtime_group_profile (data_as_of DESC, source_group_id);
