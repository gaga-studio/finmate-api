-- 인증에 필요한 최소한만 둔다.
--
-- 이전 스키마는 finmate_user 한 테이블에 온보딩 설문·알림 취향·공개 프로필 동의까지
-- 24개 컬럼을 얹고 있었다. 그 화면들이 없어졌고, 금융 프로필은 원장 쪽 관심사라
-- 여기 두지 않는다(V2).

CREATE TABLE finmate_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    display_name  VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- refresh token은 원문을 저장하지 않는다. 유출돼도 그대로는 쓸 수 없어야 한다.
CREATE TABLE finmate_refresh (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES finmate_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

-- 로그아웃·재발급에서 "이 사용자의 살아 있는 토큰"을 찾는 경로가 가장 잦다.
CREATE INDEX idx_refresh_user_active ON finmate_refresh (user_id) WHERE revoked_at IS NULL;
