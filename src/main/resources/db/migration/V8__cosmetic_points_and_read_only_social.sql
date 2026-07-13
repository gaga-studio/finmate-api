ALTER TABLE finmate_quest ADD COLUMN point_reward INTEGER NOT NULL DEFAULT 0;
ALTER TABLE finmate_quest ADD CONSTRAINT finmate_quest_point_reward_nonnegative CHECK (point_reward >= 0);

ALTER TABLE finmate_quest_completion ADD COLUMN points_awarded INTEGER NOT NULL DEFAULT 0;
ALTER TABLE finmate_quest_completion ADD CONSTRAINT finmate_quest_completion_points_nonnegative CHECK (points_awarded >= 0);

CREATE TABLE finmate_cosmetic_catalog (
    id VARCHAR(64) PRIMARY KEY,
    item_type VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price_points INTEGER NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL,
    CHECK (item_type IN ('OUTFIT', 'PROFILE_FRAME', 'THEME')),
    CHECK (price_points > 0),
    UNIQUE (display_order)
);

CREATE TABLE finmate_point_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    entry_type VARCHAR(16) NOT NULL,
    amount_points INTEGER NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (entry_type IN ('EARN', 'SPEND')),
    CHECK ((entry_type = 'EARN' AND amount_points > 0) OR (entry_type = 'SPEND' AND amount_points < 0)),
    UNIQUE (user_id, idempotency_key),
    UNIQUE (user_id, source_type, source_id)
);

CREATE INDEX finmate_point_ledger_user_time_idx ON finmate_point_ledger (user_id, occurred_at DESC);

CREATE TABLE finmate_user_cosmetic (
    user_id UUID NOT NULL REFERENCES finmate_user(id),
    cosmetic_id VARCHAR(64) NOT NULL REFERENCES finmate_cosmetic_catalog(id),
    acquired_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, cosmetic_id)
);

INSERT INTO finmate_cosmetic_catalog (id, item_type, name, description, price_points, available, display_order) VALUES
    ('cosmetic-outfit-mint', 'OUTFIT', '민트 탐험복', '캐릭터에 적용하는 고정형 의상', 5, TRUE, 1),
    ('cosmetic-frame-sprout', 'PROFILE_FRAME', '새싹 프로필 테두리', '프로필에 적용하는 고정형 테두리', 15, TRUE, 2),
    ('cosmetic-theme-daylight', 'THEME', '맑은 낮 테마', '앱 배경에 적용하는 고정형 테마', 25, TRUE, 3);

CREATE TABLE finmate_social_friend (
    id VARCHAR(64) PRIMARY KEY,
    alias VARCHAR(120) NOT NULL,
    avatar_code VARCHAR(64) NOT NULL,
    quest_completed_today BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL UNIQUE
);

CREATE TABLE finmate_social_feed_event (
    id UUID PRIMARY KEY,
    friend_id VARCHAR(64) NOT NULL REFERENCES finmate_social_friend(id),
    event_type VARCHAR(32) NOT NULL,
    message VARCHAR(255) NOT NULL,
    completed BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    display_order INTEGER NOT NULL UNIQUE,
    CHECK (event_type IN ('QUEST', 'ROUTINE', 'STREAK'))
);

CREATE TABLE finmate_social_streak (
    id UUID PRIMARY KEY,
    friend_id VARCHAR(64) NOT NULL REFERENCES finmate_social_friend(id),
    label VARCHAR(120) NOT NULL,
    days_together INTEGER NOT NULL,
    display_order INTEGER NOT NULL UNIQUE,
    CHECK (days_together >= 0)
);

INSERT INTO finmate_social_friend (id, alias, avatar_code, quest_completed_today, display_order) VALUES
    ('friend-mint', '민트 등불', 'BEAR_MINT', TRUE, 1),
    ('friend-blue', '파란 나침반', 'SEAL_BLUE', TRUE, 2),
    ('friend-gold', '금빛 새싹', 'RABBIT_GOLD', TRUE, 3),
    ('friend-violet', '보랏빛 책갈피', 'BIRD_VIOLET', FALSE, 4),
    ('friend-gray', '회색 구름', 'BEAR_GRAY', FALSE, 5);

INSERT INTO finmate_social_feed_event (id, friend_id, event_type, message, completed, occurred_at, display_order) VALUES
    ('00000000-0000-0000-0000-000000000301', 'friend-mint', 'QUEST', '오늘 예산 확인 퀘스트를 완료했어요', TRUE, '2026-07-13T09:10:00Z', 1),
    ('00000000-0000-0000-0000-000000000302', 'friend-blue', 'ROUTINE', '이번 주 자동저축 루틴을 유지했어요', TRUE, '2026-07-13T08:30:00Z', 2),
    ('00000000-0000-0000-0000-000000000303', 'friend-violet', 'STREAK', '금융 기록을 오늘도 이어가고 있어요', FALSE, '2026-07-13T07:50:00Z', 3);

INSERT INTO finmate_social_streak (id, friend_id, label, days_together, display_order) VALUES
    ('00000000-0000-0000-0000-000000000311', 'friend-mint', '예산 확인 함께하기', 18, 1),
    ('00000000-0000-0000-0000-000000000312', 'friend-blue', '자동저축 함께하기', 11, 2);
