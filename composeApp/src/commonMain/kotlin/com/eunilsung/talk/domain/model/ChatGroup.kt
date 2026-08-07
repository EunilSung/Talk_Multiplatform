package com.eunilsung.talk.domain.model

/** 대화방 그룹 — 대화함 상단의 필터 칩. */
data class ChatGroup(
    val id: String,
    val name: String,
    val kind: String,
    val sort: String,
    val roomIds: List<String>,
)
