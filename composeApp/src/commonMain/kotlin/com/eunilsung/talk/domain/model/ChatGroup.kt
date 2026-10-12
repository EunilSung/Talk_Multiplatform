package com.eunilsung.talk.domain.model

/** 대화방 그룹 — 대화함 상단의 필터 칩. */
data class ChatGroup(
    val id: String,
    val name: String,
    /** [KIND_UNREAD] 또는 [KIND_CUSTOM]. */
    val kind: String,
    val sort: String,
    val roomIds: List<String>,
) {
    /** 안읽음 칩. 이름은 화면이 붙인다. */
    val isUnreadGroup: Boolean get() = kind == KIND_UNREAD

    /** 사용자가 만든 그룹. */
    val isCustomGroup: Boolean get() = kind == KIND_CUSTOM

    companion object {
        const val KIND_UNREAD = "1"
        const val KIND_CUSTOM = "2"
    }
}
