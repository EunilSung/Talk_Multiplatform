-- 파일 대화가 실은 파일, 투표를 알린 대화의 번호, 하루 올리기 한도용 색인.
--
-- 파일 대화의 파일 id 를 부가 정보(JSON)에서 꺼내 믿으면, 아무 대화에 남의 파일 id 를 적어 두고 회수해 그 파일을
-- 지울 수 있다. 서버가 확인한 파일만 이 열에 적고, 회수·내려받기·다시 싣기 확인은 이 열로 한다.
ALTER TABLE chat_message ADD COLUMN file_id TEXT;

-- 이미 있는 파일 대화는 그 방에 실제로 올라온 파일을 가리킬 때만 채운다.
UPDATE chat_message g SET file_id = f.id
FROM chat_file f
WHERE g.kind IN ('image', 'video', 'file') AND g.payload IS NOT NULL
  AND f.room_id = g.room_id AND f.id = (g.payload::jsonb ->> 'fileId');

CREATE INDEX chat_message_file_idx ON chat_message (file_id) WHERE file_id IS NOT NULL;

-- 투표를 알린 대화의 번호. 나중에 초대받은 사람에게 그 전 투표를 보여 주지 않는 데 쓴다.
-- 알림 대화를 찾지 못한 옛 투표는 비어 있고, 방 참여자 모두에게 보인다.
ALTER TABLE chat_vote ADD COLUMN seq BIGINT;

UPDATE chat_vote v SET seq = g.seq
FROM chat_message g
WHERE g.room_id = v.room_id AND g.kind = 'vote' AND g.payload IS NOT NULL
  AND (g.payload::jsonb -> 'vote' ->> 'id') = v.id;

-- 한 사람이 하루에 올린(전달로 복사한 것 포함) 파일의 개수와 용량을 셀 때 쓴다.
CREATE INDEX chat_file_uploader_idx ON chat_file (uploader_id, created_at);
