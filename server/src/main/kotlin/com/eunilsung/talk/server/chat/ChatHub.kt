package com.eunilsung.talk.server.chat

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.push.ChatPushService
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.ServerEvent
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * 지금 붙어 있는 WebSocket 연결들.
 *
 * 한 사람이 여러 기기로 붙을 수 있어 사람마다 연결을 여러 개 둔다. 보내다 실패한 연결은 조용히
 * 넘어간다 — 끊긴 쪽은 다시 붙을 때 REST 로 놓친 것을 받아 가므로, 여기서 재시도할 이유가 없다.
 *
 * 연결마다 어느 로그인(토큰 해시)으로 열렸는지 함께 둔다. 로그아웃하면 그 로그인으로 열린 연결을 닫는다 —
 * 토큰을 지워도 이미 열린 연결이 그 사람의 대화를 계속 받으면 로그아웃한 것이 아니다.
 * 한 사람이 열 수 있는 연결은 [maxPerUser] 개다. 넘으면 가장 오래된 것을 닫는다.
 */
class ChatHub(
    private val push: ChatPushService? = null,
    private val maxPerUser: Int = Int.MAX_VALUE,
) {

    private class Connection(val session: WebSocketSession, val tokenHash: String, val order: Long)

    private val sessions = ConcurrentHashMap<String, MutableSet<Connection>>()

    private val order = AtomicLong()

    private val messageListeners = CopyOnWriteArrayList<(MessageDto) -> Unit>()

    /** 새 대화가 나갈 때마다 불릴 것을 등록한다. 부르는 쪽을 막지 않게, 등록하는 쪽이 일을 따로 띄워야 한다. */
    fun onNewMessage(listener: (MessageDto) -> Unit) {
        messageListeners += listener
    }

    /** [tokenHash] 로그인으로 열린 [session] 을 붙인다. 이 사람의 연결이 [maxPerUser] 를 넘으면 가장 오래된 것을 닫는다. */
    suspend fun join(userId: String, tokenHash: String, session: WebSocketSession) {
        val connection = Connection(session, tokenHash, order.incrementAndGet())
        var overflow = emptyList<Connection>()
        sessions.compute(userId) { _, current ->
            val set = current ?: CopyOnWriteArraySet()
            set.add(connection)
            overflow = set.sortedBy { it.order }.dropLast(maxPerUser)
            set.removeAll(overflow.toSet())
            set
        }
        overflow.forEach { it.close(TOO_MANY_CONNECTIONS) }
    }

    /** 이 사람의 연결이 하나라도 붙어 있는지. */
    fun isOnline(userId: String): Boolean = sessions.containsKey(userId)

    fun leave(userId: String, session: WebSocketSession) {
        sessions.computeIfPresent(userId) { _, set ->
            set.removeAll { it.session === session }
            set.takeIf { it.isNotEmpty() }
        }
    }

    /** [tokenHash] 로그인으로 열린 연결을 모두 닫는다. 로그아웃할 때 부른다. */
    suspend fun closeLogin(tokenHash: String) {
        val closing = mutableListOf<Connection>()
        sessions.keys.forEach { userId ->
            sessions.computeIfPresent(userId) { _, set ->
                val matched = set.filter { it.tokenHash == tokenHash }
                set.removeAll(matched.toSet())
                closing += matched
                set.takeIf { it.isNotEmpty() }
            }
        }
        closing.forEach { it.close(LOGGED_OUT) }
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
            sessions[userId]?.forEach { connection ->
                runCatching { connection.session.send(Frame.Text(frameText)) }
            }
        }
    }

    private suspend fun Connection.close(reason: CloseReason) {
        runCatching { session.close(reason) }
    }

    private companion object {
        val LOGGED_OUT = CloseReason(CloseReason.Codes.VIOLATED_POLICY, ApiErrorCode.UNAUTHORIZED)
        val TOO_MANY_CONNECTIONS = CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "too_many_connections")
    }
}
