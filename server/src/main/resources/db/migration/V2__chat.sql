-- 대화방과 대화.

CREATE TABLE chat_room (
    id         TEXT PRIMARY KEY,
    title      TEXT   NOT NULL DEFAULT '',
    -- 방 안에서 다음 대화에 매길 번호. UPDATE ... RETURNING 으로 원자적으로 뽑는다.
    next_seq   BIGINT NOT NULL DEFAULT 1,
    -- 1:1 방과 나와의 대화방의 신원 — 참여자 아이디를 정렬해 이어 붙인 값.
    -- "이미 방이 있으면 새로 만들지 않고 그 방으로 간다"를 유일 제약으로 지킨다. 조회 후 삽입으로
    -- 하면 두 사람이 동시에 서로를 눌렀을 때 방이 둘 생긴다. 단체방은 NULL 이라 제약에 걸리지 않는다.
    direct_key TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_room_member (
    room_id       TEXT   NOT NULL REFERENCES chat_room (id) ON DELETE CASCADE,
    user_id       TEXT   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- 이 번호보다 뒤의 대화만 보인다. 나중에 초대받은 사람에게 그 전 대화를 보여 주지 않는다.
    joined_seq    BIGINT NOT NULL DEFAULT 0,
    -- 어디까지 읽었는지. 안읽음 수는 저장하지 않고 이 값에서 계산한다 — 따로 저장하면 어긋난다.
    last_read_seq BIGINT NOT NULL DEFAULT 0,
    -- 나간 시각. NULL 이면 참여 중이다. 행을 지우지 않는 이유는 1:1 방에서 떠난 상대를 보여 줘야 해서다.
    left_at       TIMESTAMPTZ,
    is_muted      BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (room_id, user_id)
);

-- "내가 참여 중인 방" 조회가 쓴다.
CREATE INDEX chat_room_member_user_idx ON chat_room_member (user_id) WHERE left_at IS NULL;

CREATE TABLE chat_message (
    room_id   TEXT   NOT NULL REFERENCES chat_room (id) ON DELETE CASCADE,
    seq       BIGINT NOT NULL,
    -- 보낸 앱이 만든 id. 재전송이 겹쳐도 같은 대화가 두 번 들어가지 않게 유일 제약을 건다.
    client_id TEXT   NOT NULL,
    sender_id TEXT   NOT NULL REFERENCES app_user (id),
    kind      TEXT   NOT NULL DEFAULT 'text',
    content   TEXT   NOT NULL DEFAULT '',
    -- 종류별 부가 정보(JSON). 종류마다 모양이 달라 열로 펴지 않는다.
    payload   TEXT,
    sent_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, seq),
    UNIQUE (room_id, client_id)
);
