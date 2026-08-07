package com.eunilsung.talk.domain.model

/** 대화방 공지 — 방마다 최대 하나. */
data class Notice(
    val noticeId: String,
    val chatRoomId: String,
    val content: String,
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerPosition: String = "",
    val date: String = "",
) {
    /** 공지 식별 키 — 영속 UI state 의 키로 사용. */
    val identityKey: String
        get() {
            if (noticeId.isNotBlank()) return noticeId
            if (ownerId.isNotBlank() || date.isNotBlank()) return "$ownerId.$date"
            return "content:${content.hashCode()}"
        }

    companion object {
        /** 등록/삭제 action 값. */
        const val ACTION_ADD = "ADD"
        const val ACTION_DELETE = "DEL"

        /** 공지 대화의 chatType flag. */
        const val CHAT_TYPE_FLAG = "262144"
    }
}
