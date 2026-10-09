-- 푸시 토큰.
--
-- 토큰이 기본 키다. 한 기기에서 로그아웃하고 다른 계정으로 들어와도 FCM 토큰은 그대로라,
-- 계정을 키로 잡으면 옛 주인에게 가던 알림이 계속 간다. 토큰을 키로 두고 주인을 덮어쓴다.
--
-- 어느 로그인으로 등록했는지를 함께 적는다. 그 로그인이 끝나면(로그아웃) 이 행도 같이 지워져,
-- 로그아웃한 기기로 알림이 가지 않는다.
CREATE TABLE push_token (
    token           TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    platform        TEXT NOT NULL,
    auth_token_hash TEXT NOT NULL REFERENCES auth_token (token_hash) ON DELETE CASCADE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX push_token_user_idx ON push_token (user_id);
