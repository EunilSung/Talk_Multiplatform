package com.eunilsung.talk.domain.model

/** 사용자 그룹 추가 결과 — 3-state */
enum class AddUserResult {
    SUCCESS,
    /** 이미 그룹에 있는 사용자 (안내 문구용으로 구분). */
    ALREADY_EXISTS,
    FAILED
}
