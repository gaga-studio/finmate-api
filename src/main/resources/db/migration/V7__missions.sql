-- 미션과 포인트.
--
-- 앱의 미션은 두 갈래다. 하나는 예산 챌린지처럼 **원장에서 판정되는** 것 —
-- 사용자가 "했다"고 누르는 게 아니라 그날 얼마 썼는지가 결과를 정한다.
-- 다른 하나는 퀴즈처럼 행동으로 완료되는 것이다.
--
-- 그래서 진행률을 저장하지 않는다. 저장하면 원장과 어긋날 수 있고, 어긋난 쪽이 화면에 뜬다.
-- 원장에서 매번 판정하고, 여기에는 "무엇을 담았는가"와 "보상을 줬는가"만 남긴다.

CREATE TABLE mission (
    id          VARCHAR(32) PRIMARY KEY,
    title       VARCHAR(80)  NOT NULL,
    -- budget-daily · saving · quiz. 판정 방식이 다르다.
    kind        VARCHAR(16)  NOT NULL,
    reward      INT          NOT NULL,
    -- 왜 이 미션을 추천하는지. 발표자료의 "해체분석 근거"에 해당한다.
    reason      VARCHAR(160) NOT NULL
);

CREATE TABLE persona_mission (
    persona_id  UUID        NOT NULL REFERENCES persona (id) ON DELETE CASCADE,
    mission_id  VARCHAR(32) NOT NULL REFERENCES mission (id),
    -- 담은 날. 판정 구간의 시작이기도 하다 — 담기 전의 지출로 보상을 주면 안 된다.
    accepted_on DATE        NOT NULL,
    PRIMARY KEY (persona_id, mission_id)
);

-- 포인트는 잔액을 저장하지 않고 원장으로 쌓는다.
-- 잔액만 두면 "왜 이만큼인가"를 되짚을 수 없고, 이중 지급을 발견할 방법도 없다.
CREATE TABLE point_ledger (
    id          BIGSERIAL PRIMARY KEY,
    persona_id  UUID        NOT NULL REFERENCES persona (id) ON DELETE CASCADE,
    amount      INT         NOT NULL,
    reason      VARCHAR(80) NOT NULL,
    -- 같은 미션의 같은 날 판정으로 두 번 주지 않는다.
    idempotency_key VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_point_idem UNIQUE (persona_id, idempotency_key)
);

CREATE INDEX idx_point_persona ON point_ledger (persona_id);

-- 기본 미션. 앱의 "추천 미션"에 해당하며, 추천 근거를 함께 적는다 —
-- 근거 없는 미션은 "왜 나한테 이걸 시키지"가 되어 실행으로 이어지지 않는다.
INSERT INTO mission (id, title, kind, reward, reason) VALUES
  ('keep-daily-budget', '하루 예산 지키기',   'budget-daily', 80,  '최근 하루 예산을 넘긴 날이 있어요'),
  ('save-monthly-goal', '이번 달 저축 목표',  'saving',       120, '목표 저축률까지 아직 여유가 있어요'),
  ('quiz-emergency',    '비상금 퀴즈 풀기',   'quiz',         60,  '비상금은 첫 금융 행동으로 가장 자주 권해져요');
