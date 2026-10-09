-- 사용자와 인증 토큰. 나머지 표는 전부 이 둘 위에 선다.

-- 사용자. id 가 로그인 아이디다.
--
-- 비밀번호는 해시로만 둔다. 이 표가 새어도 그것만으로는 로그인할 수 없다.
CREATE TABLE app_user (
    id             TEXT PRIMARY KEY,
    password_hash  TEXT NOT NULL,
    name           TEXT NOT NULL,
    organ_name     TEXT NOT NULL DEFAULT '',
    position_name  TEXT NOT NULL DEFAULT '',
    -- 그룹 안에서 사람을 세우는 순서. 직급이 높을수록 작다.
    position_sort  INT  NOT NULL DEFAULT 99,
    email          TEXT NOT NULL DEFAULT '',
    phone_number   TEXT NOT NULL DEFAULT '',
    birthday       TEXT NOT NULL DEFAULT '',
    status_message TEXT NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 인증 토큰. 로그인하면 하나 내주고, 이후 요청은 토큰이 신원을 정한다.
--
-- 토큰 자체가 아니라 해시를 저장한다. 이 표가 새어도 그것만으로는 남의 계정을 쓸 수 없다.
CREATE TABLE auth_token (
    token_hash   TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

-- 한 사람이 기기마다 토큰을 하나씩 가진다. 계정 단위로 훑을 일(전부 무효화)이 있어 색인을 둔다.
CREATE INDEX auth_token_user_idx ON auth_token (user_id);
