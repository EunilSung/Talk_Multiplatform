-- 대화의 부가 정보와 투표 마감 시각의 길이 상한.
--
-- 부가 정보(JSON)는 방을 여는 사람마다 읽힌다(대화 목록·방 목록의 마지막 대화). 한 칸이라도 상한이 빠지면 큰 대화 몇 건으로
-- 그 방을 여는 요청마다 서버 메모리가 바닥난다. 서버가 칸마다 상한을 지키지만, 빠뜨린 칸이 있어도 여기서 막는다.
-- 가장 큰 정상 부가 정보는 투표 알림이다(제목 200자 + 항목 20개 × 200자, 이스케이프를 넣어도 3만 자 안).
--
-- NOT VALID — 이미 있는 행은 검사하지 않는다. 배포된 DB 에 큰 행이 남아 있어도 마이그레이션이 멈추지 않는다.
ALTER TABLE chat_message
    ADD CONSTRAINT chat_message_payload_size CHECK (payload IS NULL OR char_length(payload) <= 32768) NOT VALID;

-- 투표 마감 시각은 앱이 정한 짧은 날짜·시각 문자열이다. 투표 알림의 부가 정보에 그대로 실린다.
ALTER TABLE chat_vote
    ADD CONSTRAINT chat_vote_end_time_size CHECK (char_length(end_time) <= 32) NOT VALID;
