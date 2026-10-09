-- 대화에 딸린 파일. 바이트는 디스크에 두고 여기에는 누가 어느 방에 올렸는지만 적는다.
--
-- 방에 묶어 두는 이유는 권한이다. 내려받을 때 "그 방의 참여자인가"를 이 표로 확인한다.
CREATE TABLE chat_file (
    id          TEXT PRIMARY KEY,
    room_id     TEXT   NOT NULL REFERENCES chat_room (id) ON DELETE CASCADE,
    uploader_id TEXT   NOT NULL REFERENCES app_user (id),
    name        TEXT   NOT NULL,
    size        BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
