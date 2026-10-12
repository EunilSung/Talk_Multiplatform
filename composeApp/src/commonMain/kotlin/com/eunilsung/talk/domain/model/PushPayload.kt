package com.eunilsung.talk.domain.model

/** 푸시(FCM) data payload 의 정형 표현. */
data class PushPayload(
    val msg: String,
    val msgKey: String,
    val kind: Kind,
    val msgType: String,
    val senderName: String,
    val unreadCount: Int,
    val categoryId: String,
) {
    /** 본문 없이 이모티콘만 보낸 대화. 알림은 기기 언어의 대체 문구(`push_emoticon`)를 본문으로 쓴다. */
    val isEmoticonOnly: Boolean get() = msgType == CHAT_TYPE_EMOTICON && msg.isEmpty()

    /** 푸시 종류. `msgkind` 의 정형화. */
    enum class Kind {
        /** 대화 메시지 (대화방 알림) */
        CHAT,
        /** 배지만 갱신 — 알림 표시 X */
        BADGE,
        /** 알 수 없음 — 무시 */
        UNKNOWN;

        companion object {
            fun from(raw: String?): Kind = when (raw?.uppercase()) {
                "TALK" -> CHAT
                "BADGE" -> BADGE
                else -> UNKNOWN
            }
        }
    }

    companion object {
        /** 알림 표시 제외 chat 타입 코드 — INVITE(256), EXIT(512). */
        val CHAT_TYPE_INVITE = "256"
        val CHAT_TYPE_EXIT = "512"
        /** 이모티콘 본문 코드 — msg 가 비어 있으면 [isEmoticonOnly]. */
        val CHAT_TYPE_EMOTICON = "32"
    }
}
