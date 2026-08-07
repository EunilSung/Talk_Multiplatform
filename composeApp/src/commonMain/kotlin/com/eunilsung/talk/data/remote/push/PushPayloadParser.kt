package com.eunilsung.talk.data.remote.push

import io.ktor.http.decodeURLQueryComponent
import com.eunilsung.talk.domain.model.PushPayload

/** Raw push data map → [PushPayload] 변환 (값은 URL-decode, 누락 키는 빈 문자열/0). */
class PushPayloadParser {

    fun parse(data: Map<String, String>): PushPayload {
        fun get(key: String): String = data[key]?.let { decode(it) } ?: ""

        val msg = get(KEY_MSG)
        val msgType = get(KEY_MSGTYPE)
        val body = if (msgType == PushPayload.CHAT_TYPE_EMOTICON && msg.isEmpty()) "(이모티콘)" else msg

        return PushPayload(
            msg = body,
            msgKey = get(KEY_MSG_KEY),
            kind = PushPayload.Kind.from(get(KEY_MSG_KIND).ifEmpty { null }),
            msgType = msgType,
            senderName = get(KEY_SENDER_NAME),
            unreadCount = get(KEY_UNREAD_COUNT).toIntOrNull() ?: 0,
            categoryId = get(KEY_CATEGORY_ID),
        )
    }

    private fun decode(raw: String): String = runCatching {
        raw.decodeURLQueryComponent(plusIsSpace = false)
    }.getOrDefault(raw)

    companion object {
        const val KEY_MSG = "msg"
        const val KEY_MSG_KEY = "msgkey"
        const val KEY_MSG_KIND = "msgkind"
        const val KEY_MSGTYPE = "msgtype"
        const val KEY_SENDER_NAME = "senderName"
        const val KEY_UNREAD_COUNT = "unReadCount"
        const val KEY_CATEGORY_ID = "msgCategoryId"
    }
}
