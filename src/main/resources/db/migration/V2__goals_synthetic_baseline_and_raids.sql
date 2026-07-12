CREATE TABLE finmate_onboarding_state (
    user_id UUID PRIMARY KEY REFERENCES finmate_user(id),
    display_name VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128)
);

CREATE TABLE finmate_user_goal (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    title VARCHAR(255) NOT NULL,
    domain VARCHAR(16) NOT NULL,
    current_amount_krw BIGINT NOT NULL,
    target_amount_krw BIGINT NOT NULL,
    target_month DATE NOT NULL,
    state VARCHAR(16) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    data_state VARCHAR(16) NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    CHECK (current_amount_krw >= 0),
    CHECK (target_amount_krw > current_amount_krw),
    CHECK (domain IN ('SPENDING', 'SAVING')),
    CHECK (state IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CHECK (data_state IN ('FRESH', 'PENDING', 'STALE', 'INSUFFICIENT'))
);

CREATE UNIQUE INDEX finmate_user_goal_one_active_idx
    ON finmate_user_goal (user_id)
    WHERE state = 'ACTIVE';

CREATE TABLE finmate_synthetic_financial_snapshot (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    goal_id UUID NOT NULL REFERENCES finmate_user_goal(id),
    snapshot_month DATE NOT NULL,
    observed_goal_amount_krw BIGINT NOT NULL,
    spending_bps INTEGER NOT NULL,
    saving_bps INTEGER NOT NULL,
    investment_judgment_bps INTEGER NOT NULL,
    xp INTEGER NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    CHECK (observed_goal_amount_krw >= 0),
    CHECK (spending_bps BETWEEN 0 AND 10000),
    CHECK (saving_bps BETWEEN 0 AND 10000),
    CHECK (investment_judgment_bps BETWEEN 0 AND 10000),
    CHECK (xp >= 0)
);

CREATE INDEX finmate_snapshot_month_idx
    ON finmate_synthetic_financial_snapshot (user_id, snapshot_month, last_synced_at DESC);

CREATE TABLE finmate_raid_projection (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL UNIQUE REFERENCES finmate_user_goal(id),
    confirmed_baseline_amount_krw BIGINT NOT NULL,
    current_progress_bps INTEGER NOT NULL,
    highest_progress_bps INTEGER NOT NULL,
    stage INTEGER NOT NULL,
    boss_hp_bps INTEGER NOT NULL,
    coach_copy_key VARCHAR(128) NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    data_state VARCHAR(16) NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    CHECK (current_progress_bps BETWEEN 0 AND 10000),
    CHECK (highest_progress_bps BETWEEN current_progress_bps AND 10000),
    CHECK (stage BETWEEN 1 AND 3),
    CHECK (boss_hp_bps BETWEEN 0 AND 10000),
    CHECK (data_state IN ('FRESH', 'PENDING', 'STALE', 'INSUFFICIENT'))
);

CREATE TABLE finmate_raid_projection_audit (
    id UUID PRIMARY KEY,
    raid_id UUID NOT NULL REFERENCES finmate_raid_projection(id),
    current_progress_bps INTEGER NOT NULL,
    highest_progress_bps INTEGER NOT NULL,
    stage INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CHECK (current_progress_bps BETWEEN 0 AND 10000),
    CHECK (highest_progress_bps BETWEEN current_progress_bps AND 10000),
    CHECK (stage BETWEEN 1 AND 3)
);

CREATE INDEX finmate_raid_projection_audit_raid_idx
    ON finmate_raid_projection_audit (raid_id, recorded_at DESC);
