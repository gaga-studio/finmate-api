-- 그림일기.
--
-- 하루가 끝나면 그날의 거래에서 주인공을 뽑아 그림 한 장으로 남긴다. 그림체는 날마다 달라진다.
-- 앱 기획의 핵심 기능이고, 지금은 정적 파일 22장이던 것을 실제 생성으로 바꾼다.
--
-- 생성은 외부 API 호출이고 수 초가 걸린다. 요청-응답 안에서 하면 안 되므로 이 테이블이
-- 작업 상태를 들고 간다. 여기서 다루는 문제는 콘서트(동시성)나 채팅(실시간)과 다르다 —
-- 내가 통제할 수 없는 것에 기대는 일이다.

CREATE TABLE diary_entry (
    id              BIGSERIAL PRIMARY KEY,
    persona_id      UUID        NOT NULL REFERENCES persona (id) ON DELETE CASCADE,
    entry_date      DATE        NOT NULL,

    -- 그날의 주인공. 원장의 flow 합계에서 가장 큰 것.
    dominant_flow   VARCHAR(8)  NOT NULL,
    -- 그림에 들어갈 장면의 근거가 된 거래
    subject         VARCHAR(120) NOT NULL,
    art_style       VARCHAR(24) NOT NULL,

    -- 어떤 프롬프트로 만들었는지 남긴다. 그림이 이상할 때 되짚을 수 있어야 하고,
    -- 같은 그림을 다시 만들 수도 있어야 한다.
    prompt          TEXT        NOT NULL,

    status          VARCHAR(12) NOT NULL,
    -- 외부 큐의 작업 id. 서버가 재시작해도 이걸로 결과를 다시 찾는다.
    provider_job_id VARCHAR(120),
    image_path      VARCHAR(200),

    attempts        SMALLINT    NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 제출 시각. 오래 머무는 작업을 회수하는 기준이 된다.
    submitted_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,

    -- 하루에 한 장. 같은 날 요청이 두 번 와도 그림은 하나다.
    CONSTRAINT uq_diary_persona_date UNIQUE (persona_id, entry_date),
    CONSTRAINT ck_diary_status CHECK (status IN ('PENDING', 'SUBMITTED', 'READY', 'FAILED')),
    CONSTRAINT ck_diary_flow CHECK (dominant_flow IN ('소비', '저축', '투자'))
);

-- 워커가 "할 일"을 집는 경로. 대부분의 행은 READY라 부분 인덱스로 충분하다.
CREATE INDEX idx_diary_pending ON diary_entry (created_at)
    WHERE status IN ('PENDING', 'SUBMITTED');
