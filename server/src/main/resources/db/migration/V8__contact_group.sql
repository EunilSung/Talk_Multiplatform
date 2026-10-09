-- 내그룹 — 사람마다 따로 가지는 연락처 묶음.
--
-- 대화그룹과 같은 방식이다. id 는 앱이 만든 값이라 사람과 묶어 기본 키로 두고,
-- 앱이 고칠 때마다 전체 목록을 올려 통째로 바꾼다.
CREATE TABLE contact_group (
    user_id TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    id      TEXT NOT NULL,
    name    TEXT NOT NULL,
    sort    INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, id)
);

CREATE TABLE contact_group_member (
    user_id   TEXT NOT NULL,
    group_id  TEXT NOT NULL,
    member_id TEXT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id, member_id),
    FOREIGN KEY (user_id, group_id) REFERENCES contact_group (user_id, id) ON DELETE CASCADE
);
