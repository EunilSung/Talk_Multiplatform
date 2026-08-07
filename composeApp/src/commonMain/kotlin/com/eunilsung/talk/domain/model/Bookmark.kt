package com.eunilsung.talk.domain.model

/** 대화 책갈피 — 사용자가 특정 대화를 즐겨찾기한 항목. */
data class Bookmark(
    val chatId: String,
    val chatRoomId: String,
    val content: String,
    val date: String,
    val userId: String,
    val userName: String,
)
