-- 회수·공감·공지·책갈피.

-- 회수. 행을 지우지 않고 시각만 적는다 — 번호가 비면 안읽음 계산과 순서가 흔들린다.
-- 회수된 대화의 본문은 조회할 때 비워서 내보낸다.
ALTER TABLE chat_message ADD COLUMN recalled_at TIMESTAMPTZ;

-- 공감. 한 사람이 한 대화에 하나만 누를 수 있어 (방, 대화, 사람)이 기본 키다.
CREATE TABLE chat_reaction (
    room_id    TEXT   NOT NULL,
    seq        BIGINT NOT NULL,
    user_id    TEXT   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- 반응 종류 '0'~'5'. 앱의 이모지 순서와 같다.
    kind       TEXT   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, seq, user_id),
    FOREIGN KEY (room_id, seq) REFERENCES chat_message (room_id, seq) ON DELETE CASCADE
);

-- 공지. 방마다 하나만 유지하므로 방이 기본 키다.
CREATE TABLE chat_notice (
    room_id    TEXT PRIMARY KEY REFERENCES chat_room (id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    content    TEXT NOT NULL,
    owner_id   TEXT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 책갈피. 사람마다 따로 가진다.
CREATE TABLE chat_bookmark (
    user_id    TEXT   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    room_id    TEXT   NOT NULL,
    seq        BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, room_id, seq),
    FOREIGN KEY (room_id, seq) REFERENCES chat_message (room_id, seq) ON DELETE CASCADE
);
