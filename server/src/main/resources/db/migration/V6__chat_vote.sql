-- 투표.

CREATE TABLE chat_vote (
    id             TEXT PRIMARY KEY,
    room_id        TEXT NOT NULL REFERENCES chat_room (id) ON DELETE CASCADE,
    title          TEXT NOT NULL,
    writer_id      TEXT NOT NULL REFERENCES app_user (id),
    multi_select   BOOLEAN NOT NULL DEFAULT false,
    allow_add_item BOOLEAN NOT NULL DEFAULT false,
    use_end_time   BOOLEAN NOT NULL DEFAULT false,
    -- 앱이 정한 마감 시각 문자열. 서버는 보관만 하고 해석하지 않는다.
    end_time       TEXT NOT NULL DEFAULT '',
    -- 끝난 시각. NULL 이면 진행 중이다.
    closed_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX chat_vote_room_idx ON chat_vote (room_id, created_at);

CREATE TABLE chat_vote_item (
    vote_id   TEXT NOT NULL REFERENCES chat_vote (id) ON DELETE CASCADE,
    idx       INT  NOT NULL,
    content   TEXT NOT NULL,
    writer_id TEXT NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (vote_id, idx)
);

-- 표. 득표수는 따로 저장하지 않고 여기서 센다 — 따로 두면 표를 거둘 때 어긋난다.
CREATE TABLE chat_vote_ballot (
    vote_id  TEXT NOT NULL,
    idx      INT  NOT NULL,
    user_id  TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    voted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (vote_id, idx, user_id),
    FOREIGN KEY (vote_id, idx) REFERENCES chat_vote_item (vote_id, idx) ON DELETE CASCADE
);
