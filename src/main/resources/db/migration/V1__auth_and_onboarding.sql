CREATE TABLE finmate_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    onboarding_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    housing_type VARCHAR(32),
    employment_type VARCHAR(32),
    income_regularity VARCHAR(16),
    has_dependents BOOLEAN,
    primary_concern VARCHAR(32),
    change_pace VARCHAR(16),
    risk_tolerance VARCHAR(32),
    notification_preference VARCHAR(32),
    context_tags TEXT,
    profile_consent_version VARCHAR(64),
    raid_motion VARCHAR(16) NOT NULL DEFAULT 'REDUCED',
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    locale VARCHAR(16) NOT NULL DEFAULT 'ko-KR',
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul',
    privacy_id UUID NOT NULL,
    anonymous_card_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    exposed_fields TEXT NOT NULL DEFAULT '[]',
    privacy_consent_version VARCHAR(64) NOT NULL DEFAULT 'privacy-v1.0',
    privacy_version BIGINT NOT NULL DEFAULT 1,
    privacy_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    share_consent_state VARCHAR(32) NOT NULL DEFAULT 'OPTED_OUT'
);

CREATE TABLE finmate_refresh (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_at TIMESTAMPTZ
);

CREATE INDEX finmate_refresh_active_token_idx
    ON finmate_refresh (token_hash)
    WHERE revoked_at IS NULL;
