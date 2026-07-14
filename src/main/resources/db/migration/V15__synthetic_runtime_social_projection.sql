CREATE TABLE finmate_synthetic_social_friend (
    viewer_persona_id VARCHAR(64) NOT NULL,
    friend_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL,
    friend_public_id VARCHAR(64) NOT NULL,
    friend_alias VARCHAR(120) NOT NULL,
    avatar_code VARCHAR(32) NOT NULL,
    quest_completed_today BOOLEAN NOT NULL DEFAULT FALSE,
    connected_at DATE NOT NULL,
    PRIMARY KEY (viewer_persona_id, friend_persona_id, release_version),
    FOREIGN KEY (viewer_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    FOREIGN KEY (friend_persona_id, release_version)
        REFERENCES finmate_synthetic_runtime_persona(source_persona_id, release_version) ON DELETE CASCADE,
    CHECK (viewer_persona_id <> friend_persona_id),
    CHECK (friend_public_id ~ '^friend-[0-9a-f]{12}$' OR friend_public_id ~ '^[a-z0-9-]+$')
);

CREATE INDEX finmate_synthetic_social_friend_viewer_idx
    ON finmate_synthetic_social_friend (viewer_persona_id, release_version, connected_at, friend_public_id);

CREATE TABLE finmate_synthetic_social_feed_event (
    event_id VARCHAR(64) NOT NULL,
    viewer_persona_id VARCHAR(64) NOT NULL,
    subject_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    stat_delta_bps INTEGER,
    event_date DATE NOT NULL,
    PRIMARY KEY (event_id, release_version),
    FOREIGN KEY (viewer_persona_id, subject_persona_id, release_version)
        REFERENCES finmate_synthetic_social_friend(viewer_persona_id, friend_persona_id, release_version)
        ON DELETE CASCADE,
    CHECK (event_type IN ('goal_stage_clear', 'stat_up:defense_score', 'stat_up:saving_score')),
    CHECK (stat_delta_bps IS NULL OR stat_delta_bps BETWEEN 0 AND 10000)
);

CREATE INDEX finmate_synthetic_social_feed_viewer_idx
    ON finmate_synthetic_social_feed_event (viewer_persona_id, release_version, event_date DESC, event_id);

CREATE TABLE finmate_synthetic_social_streak (
    viewer_persona_id VARCHAR(64) NOT NULL,
    friend_persona_id VARCHAR(64) NOT NULL,
    release_version VARCHAR(32) NOT NULL,
    projection_version VARCHAR(32) NOT NULL,
    current_streak INTEGER NOT NULL,
    best_streak INTEGER NOT NULL,
    unit VARCHAR(16) NOT NULL,
    PRIMARY KEY (viewer_persona_id, friend_persona_id, release_version),
    FOREIGN KEY (viewer_persona_id, friend_persona_id, release_version)
        REFERENCES finmate_synthetic_social_friend(viewer_persona_id, friend_persona_id, release_version)
        ON DELETE CASCADE,
    CHECK (current_streak >= 0),
    CHECK (best_streak >= current_streak),
    CHECK (unit = '일')
);

CREATE INDEX finmate_synthetic_social_streak_viewer_idx
    ON finmate_synthetic_social_streak (viewer_persona_id, release_version, current_streak DESC, friend_persona_id);
