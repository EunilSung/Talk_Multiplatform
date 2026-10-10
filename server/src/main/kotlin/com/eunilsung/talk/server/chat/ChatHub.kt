package com.eunilsung.talk.server.chat

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.push.ChatPushService
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.ServerEvent
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 지금 붙어 있는 WebSocket 연결들.
 *
 * 한 사람이 여러 기기로 붙을 수 있어 사람마다 연결을 여러 개 둔다. 보내다 실패한 연결은 조용히
 * 넘어간다 — 끊긴 쪽은 다시 붙을 때 REST 로 놓친 것을 받아 가므로, 여기서 재시도할 이유가 없다.
 */
class ChatHub(private val push: ChatPushService? = null) {

    private val sessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    private val messageListeners = CopyOnWriteArrayList<(MessageDto) -> Unit>()

    /** 새 대화가 나갈 때마다 불릴 것을 등록한다. 부르는 쪽을 막지 않게, 등록하는 쪽이 일을 따로 띄워야 한다. */
    fun onNewMessage(listener: (MessageDto) -> Unit) {
        messageListeners += listener
    }

    fun join(userId: String, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
    }

    /** 이 사람의 연결이 하나라도 붙어 있는지. */
    fun isOnline(userId: String): Boolean = sessions.containsKey(userId)

    fun leave(userId: String, session: WebSocketSession) {
        sessions.computeIfPresent(userId) { _, set ->
            set.remove(session)
            set.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * 붙어 있는 연결로 알림을 보낸다. 새 대화는 푸시로도 내보낸다 —
     * 새 대화를 알리는 길이 여기 하나라, 여기서 함께 보내면 빠뜨리는 경로가 생기지 않는다.
     */
    suspend fun send(userIds: Collection<String>, event: ServerEvent) {
        if (event.type == ServerEvent.TYPE_MESSAGE) {
            event.message?.let { message ->
                push?.notifyNewMessage(message)
                messageListeners.forEach { it(message) }
            }
        }
        val frameText = ServerJson.encodeToString(ServerEvent.serializer(), event)
        userIds.distinct().forEach { userId ->
            sessions[userId]?.forEach { session ->
                runCatching { session.send(Frame.Text(frameText)) }
            }
        }
    }
}
