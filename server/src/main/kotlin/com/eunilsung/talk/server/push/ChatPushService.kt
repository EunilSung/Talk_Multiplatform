package com.eunilsung.talk.server.push

import com.eunilsung.talk.server.repository.PushTokenRepository
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.PushKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * 새 대화를 푸시로 내보낸다.
 *
 * WebSocket 으로 알리는 것과 따로 간다. WebSocket 은 앱이 켜져 있을 때만 닿고, 푸시는 꺼져 있어도
 * 닿는다. 둘 다 받는 경우가 생기지만 앱이 "보고 있는 방이면 알림을 띄우지 않는다"로 걸러 낸다.
 *
 * 발송은 대화 저장과 분리해 띄운다. 여기서 막히면 대화 전송 응답이 그만큼 늦어지는데, 알림 하나
 * 때문에 보내기가 느려져서는 안 된다. [sender] 가 없으면(서비스 계정 키 미설정) 아무것도 보내지 않는다.
 */
class ChatPushService(
    private val tokens: PushTokenRepository,
    private val sender: PushSender?,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(ChatPushService::class.java)

    fun notifyNewMessage(message: MessageDto) {
        val push = sender ?: return
        if (message.kind !in NOTIFIED_KINDS) return
        scope.launch {
            runCatching { deliver(push, message) }
                .onFailure { log.warn("푸시 발송 중 오류 — {}", it.message) }
        }
    }

    private suspend fun deliver(push: PushSender, message: MessageDto) {
        val targets = withContext(Dispatchers.IO) { tokens.targetsForRoom(message.roomId, message.senderId) }
        for (target in targets) {
            val data = mapOf(
                PushKeys.MSG to bodyOf(message),
                PushKeys.MSG_KEY to message.roomId,
                PushKeys.MSG_KIND to PushKeys.KIND_TALK,
                PushKeys.MSG_TYPE to (CHAT_TYPE_CODES[message.kind] ?: CHAT_TYPE_TEXT),
                PushKeys.SENDER_NAME to message.senderName,
                PushKeys.UNREAD_COUNT to target.unreadTotal.toString(),
                PushKeys.CATEGORY_ID to message.roomId,
            )
            if (push.send(target.token, data) == PushResult.UNREGISTERED) {
                withContext(Dispatchers.IO) { tokens.delete(target.token) }
            }
        }
        if (targets.isNotEmpty()) log.info("푸시 발송 — 방={} 대상={}대", message.roomId.take(8), targets.size)
    }

    /**
     * 알림에 보일 내용. 멘션 태그는 벗기고, 파일은 파일 이름을 보낸다.
     * 이모티콘만 보낸 대화는 비워 보낸다 — 앱이 자기 언어로 "(이모티콘)" 을 채운다.
     */
    private fun bodyOf(message: MessageDto): String = when (message.kind) {
        MessageKind.IMAGE, MessageKind.VIDEO, MessageKind.FILE -> message.payload?.fileName.orEmpty()
        else -> message.content.replace(MENTION_TAG, "$1")
    }

    private companion object {
        /** 초대·퇴장·투표 종료 같은 알림성 대화는 푸시로 보내지 않는다. */
        val NOTIFIED_KINDS = setOf(
            MessageKind.TEXT, MessageKind.EMOTICON, MessageKind.REPLY,
            MessageKind.IMAGE, MessageKind.VIDEO, MessageKind.FILE,
            MessageKind.VOTE, MessageKind.NOTICE,
        )

        const val CHAT_TYPE_TEXT = "0"

        /** 앱의 대화 타입 코드. 앱은 이 값으로 알림 문구를 고른다. */
        val CHAT_TYPE_CODES = mapOf(
            MessageKind.TEXT to CHAT_TYPE_TEXT,
            MessageKind.IMAGE to "1",
            MessageKind.FILE to "2",
            MessageKind.VIDEO to "4",
            MessageKind.EMOTICON to "32",
            MessageKind.VOTE to "2048",
            MessageKind.REPLY to "65536",
            MessageKind.NOTICE to "262144",
        )

        val MENTION_TAG = Regex("""<mention>([\s\S]*?)</mention>""", RegexOption.IGNORE_CASE)
    }
}
