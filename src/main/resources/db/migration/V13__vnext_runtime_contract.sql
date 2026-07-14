ALTER TABLE finmate_onboarding_state
    ADD COLUMN onboarding_state VARCHAR(24) NOT NULL DEFAULT 'GOAL_ACTIVE',
    ADD COLUMN income_regularity VARCHAR(16) NOT NULL DEFAULT 'REGULAR',
    ADD COLUMN housing_type VARCHAR(24) NOT NULL DEFAULT 'RENT',
    ADD COLUMN fixed_cost_burden VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN money_concern VARCHAR(32) NOT NULL DEFAULT 'SAVING',
    ADD COLUMN financial_tendency VARCHAR(24) NOT NULL DEFAULT 'BALANCED',
    ADD COLUMN lifestyle_tags TEXT NOT NULL DEFAULT '',
    ADD COLUMN anonymous_share_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN synthetic_mydata_consent BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN last_synced_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT finmate_onboarding_state_mode_check
        CHECK (onboarding_state IN ('EXPLORE_ONLY', 'GOAL_ACTIVE'));

CREATE TABLE finmate_goal_command (
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(512) NOT NULL,
    goal_id UUID NOT NULL REFERENCES finmate_user_goal(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key),
    CHECK (char_length(idempotency_key) BETWEEN 16 AND 128)
);

ALTER TABLE finmate_quest
    ADD COLUMN current_value INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN target_value INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT 'COUNT',
    ADD COLUMN duration_label VARCHAR(64) NOT NULL DEFAULT '오늘까지',
    ADD COLUMN accepted_at TIMESTAMPTZ,
    ADD COLUMN accept_idempotency_key VARCHAR(128),
    ADD CONSTRAINT finmate_quest_progress_check
        CHECK (current_value >= 0 AND target_value >= 0),
    ADD CONSTRAINT finmate_quest_unit_check
        CHECK (unit IN ('COUNT', 'KRW', 'BASIS_POINTS')),
    ADD CONSTRAINT finmate_quest_accept_key_check
        CHECK (accept_idempotency_key IS NULL OR char_length(accept_idempotency_key) BETWEEN 16 AND 128);

CREATE UNIQUE INDEX finmate_quest_accept_idempotency_idx
    ON finmate_quest (user_id, accept_idempotency_key)
    WHERE accept_idempotency_key IS NOT NULL;

ALTER TABLE finmate_record_event
    ADD COLUMN amount_krw BIGINT,
    DROP CONSTRAINT finmate_record_event_event_type_check,
    ADD CONSTRAINT finmate_record_event_event_type_check
        CHECK (event_type IN ('QUEST', 'ROUTINE_BUILD', 'MYDATA_RECALCULATION', 'INCOME', 'EXPENSE', 'SAVING', 'INVESTMENT'));

-- Demo timeline responses embed the old three-stage contract, so they cannot be replayed after the frame expansion.
DELETE FROM finmate_demo_timeline_command;
DELETE FROM finmate_demo_fixture_state;

ALTER TABLE finmate_demo_fixture_state
    DROP CONSTRAINT IF EXISTS finmate_demo_fixture_state_stage_check,
    ADD CONSTRAINT finmate_demo_fixture_state_frame_check
        CHECK (stage BETWEEN 0 AND 6);

ALTER TABLE finmate_demo_timeline_command
    DROP CONSTRAINT IF EXISTS finmate_demo_timeline_command_stage_check,
    DROP CONSTRAINT IF EXISTS finmate_demo_timeline_command_expected_stage_check,
    ADD CONSTRAINT finmate_demo_timeline_command_frame_check
        CHECK (stage BETWEEN 0 AND 5),
    ADD CONSTRAINT finmate_demo_timeline_command_expected_frame_check
        CHECK (request_expected_stage BETWEEN 0 AND 5);
