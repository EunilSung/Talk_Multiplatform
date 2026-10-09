-- 사람마다 다른 대화방 정리 — 상단고정과 대화그룹.

-- 고정한 시각. NULL 이면 고정하지 않았다. 최근에 고정한 방이 위로 온다.
ALTER TABLE chat_room_member ADD COLUMN pinned_at TIMESTAMPTZ;

-- 대화그룹(대화함 위쪽의 칩). id 는 앱이 만든 값이라 사람마다 겹칠 수 있어 사람과 묶어 기본 키로 둔다.
CREATE TABLE chat_group (
    user_id TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    id      TEXT NOT NULL,
    name    TEXT NOT NULL,
    sort    INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, id)
);

CREATE TABLE chat_group_room (
    user_id  TEXT NOT NULL,
    group_id TEXT NOT NULL,
    room_id  TEXT NOT NULL REFERENCES chat_room (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id, room_id),
    FOREIGN KEY (user_id, group_id) REFERENCES chat_group (user_id, id) ON DELETE CASCADE
);
