-- 멘션. 대화가 누구를 불렀는지 적어 둔다.
--
-- 방 목록의 "나를 부른 안읽은 대화 수"를 여기서 센다. 본문을 매번 뒤져 세면 방이 커질수록 느려진다.
CREATE TABLE chat_mention (
    room_id TEXT   NOT NULL,
    seq     BIGINT NOT NULL,
    user_id TEXT   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (room_id, seq, user_id),
    FOREIGN KEY (room_id, seq) REFERENCES chat_message (room_id, seq) ON DELETE CASCADE
);

-- "이 방에서 나를 부른 대화"를 찾는 조회가 쓴다.
CREATE INDEX chat_mention_user_idx ON chat_mention (user_id, room_id, seq);
