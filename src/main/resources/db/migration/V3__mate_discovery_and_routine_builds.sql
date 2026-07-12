CREATE TABLE finmate_mate_group (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    member_count INTEGER NOT NULL,
    synthetic_demo BOOLEAN NOT NULL,
    eligible_for_production_aggregation BOOLEAN NOT NULL,
    CHECK ((synthetic_demo = TRUE AND member_count = 10 AND eligible_for_production_aggregation = FALSE)
        OR (synthetic_demo = FALSE AND member_count >= 30 AND eligible_for_production_aggregation = TRUE))
);

CREATE TABLE finmate_recommended_adventurer (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL REFERENCES finmate_mate_group(id),
    alias VARCHAR(120) NOT NULL,
    similarity_reasons TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE finmate_adventurer_routine (
    id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL REFERENCES finmate_mate_group(id),
    adventurer_id VARCHAR(64) NOT NULL REFERENCES finmate_recommended_adventurer(id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    available_domains TEXT NOT NULL,
    maintained_days INTEGER NOT NULL,
    CHECK (maintained_days > 0)
);

CREATE TABLE finmate_routine_adaptation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    group_id VARCHAR(64) NOT NULL,
    adventurer_id VARCHAR(64) NOT NULL,
    source_routine_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    selected_domain VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (state IN ('AWAITING_DOMAIN', 'CANDIDATES_READY')),
    CHECK (selected_domain IS NULL OR selected_domain IN ('SPENDING', 'SAVING', 'INVESTMENT_JUDGMENT'))
);

CREATE INDEX finmate_routine_adaptation_user_idx ON finmate_routine_adaptation (user_id, created_at DESC);

CREATE TABLE finmate_routine_build (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    candidate_id VARCHAR(64) NOT NULL,
    source_routine_id VARCHAR(64) NOT NULL,
    domain VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    steps TEXT NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    replaces_build_id UUID REFERENCES finmate_routine_build(id),
    replaced_by_build_id UUID REFERENCES finmate_routine_build(id),
    command_type VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    data_state VARCHAR(16) NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    CHECK (domain IN ('SPENDING', 'SAVING', 'INVESTMENT_JUDGMENT')),
    CHECK (difficulty IN ('LIGHT', 'STANDARD', 'CHALLENGE')),
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CHECK (command_type IN ('IMPORT', 'REPLACE')),
    CHECK (data_state IN ('FRESH', 'PENDING', 'STALE', 'INSUFFICIENT')),
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128)
);

CREATE UNIQUE INDEX finmate_routine_build_one_active_idx ON finmate_routine_build (user_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX finmate_routine_build_command_idempotency_idx
    ON finmate_routine_build (user_id, command_type, idempotency_key);

INSERT INTO finmate_mate_group (id, name, member_count, synthetic_demo, eligible_for_production_aggregation) VALUES
    ('group-saving-30', 'Travel saving party', 34, FALSE, TRUE),
    ('group-demo-10', 'Demo party', 10, TRUE, FALSE);

INSERT INTO finmate_recommended_adventurer (id, group_id, alias, similarity_reasons, approved_at) VALUES
    ('adv-cobalt', 'group-saving-30', 'Cobalt Compass', 'Travel goal|Weekly saving rhythm', '2026-07-12T09:00:00Z'),
    ('adv-demo', 'group-demo-10', 'Demo Lantern', 'Demo-only routine', '2026-07-12T09:00:00Z');

INSERT INTO finmate_adventurer_routine (id, group_id, adventurer_id, title, description, available_domains, maintained_days) VALUES
    ('routine-weekly-save', 'group-saving-30', 'adv-cobalt', 'Weekly travel saving', 'A privacy-safe weekly transfer habit.', 'SPENDING,SAVING,INVESTMENT_JUDGMENT', 84),
    ('routine-demo-checkin', 'group-demo-10', 'adv-demo', 'Demo weekly check-in', 'A synthetic demo routine.', 'SAVING', 21);
