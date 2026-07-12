CREATE TABLE finmate_quest (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    template_code VARCHAR(64) NOT NULL,
    display_order INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    verification_kind VARCHAR(32) NOT NULL,
    xp_reward INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, id),
    UNIQUE (user_id, template_code),
    CHECK (display_order >= 0),
    CHECK (status IN ('AVAILABLE', 'ACTIVE', 'DATA_PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    CHECK (verification_kind IN ('BEHAVIOR', 'SYNTHETIC_MYDATA')),
    CHECK (xp_reward >= 0)
);

CREATE TABLE finmate_quest_internal_reward (
    quest_id UUID NOT NULL REFERENCES finmate_quest(id) ON DELETE CASCADE,
    reward_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (quest_id, reward_code)
);

CREATE TABLE finmate_quest_completion (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    quest_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    xp_awarded INTEGER NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (quest_id),
    UNIQUE (user_id, idempotency_key),
    FOREIGN KEY (user_id, quest_id) REFERENCES finmate_quest(user_id, id),
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
    CHECK (xp_awarded >= 0)
);

CREATE TABLE finmate_record_event (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    record_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    xp_earned INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (event_type IN ('QUEST', 'ROUTINE_BUILD', 'MYDATA_RECALCULATION')),
    CHECK (xp_earned >= 0)
);

CREATE INDEX finmate_record_event_user_date_idx ON finmate_record_event(user_id, record_date, occurred_at);

CREATE TABLE finmate_daily_reflection (
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    record_date DATE NOT NULL,
    reflection VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, record_date)
);

CREATE TABLE finmate_demo_fixture_state (
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    fixture_id VARCHAR(64) NOT NULL,
    stage INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, fixture_id),
    CHECK (fixture_id = 'EUROPE_TRAVEL_JANUARY'),
    CHECK (stage BETWEEN 0 AND 3)
);

CREATE TABLE finmate_demo_timeline_command (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    fixture_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    stage INTEGER NOT NULL,
    goal_amount_krw BIGINT NOT NULL,
    raid_progress_bps INTEGER NOT NULL,
    raid_stage INTEGER NOT NULL,
    boss_hp_bps INTEGER NOT NULL,
    spending_bps INTEGER NOT NULL,
    saving_bps INTEGER NOT NULL,
    investment_judgment_bps INTEGER NOT NULL,
    coach_copy_key VARCHAR(64) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, fixture_id, idempotency_key),
    CHECK (fixture_id = 'EUROPE_TRAVEL_JANUARY'),
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128),
    CHECK (stage BETWEEN 1 AND 3),
    CHECK (goal_amount_krw >= 0),
    CHECK (raid_progress_bps BETWEEN 0 AND 10000),
    CHECK (raid_stage BETWEEN 1 AND 3),
    CHECK (boss_hp_bps BETWEEN 0 AND 10000),
    CHECK (spending_bps BETWEEN 0 AND 10000),
    CHECK (saving_bps BETWEEN 0 AND 10000),
    CHECK (investment_judgment_bps BETWEEN 0 AND 10000)
);
