package com.eunilsung.talk.server.chat

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.shared.api.ServerEvent
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 지금 붙어 있는 WebSocket 연결들.
 *
 * 한 사람이 여러 기기로 붙을 수 있어 사람마다 연결을 여러 개 둔다. 보내다 실패한 연결은 조용히
 * 넘어간다 — 끊긴 쪽은 다시 붙을 때 REST 로 놓친 것을 받아 가므로, 여기서 재시도할 이유가 없다.
 */
class ChatHub {

    private val sessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

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

    suspend fun send(userIds: Collection<String>, event: ServerEvent) {
        val frameText = ServerJson.encodeToString(ServerEvent.serializer(), event)
        userIds.distinct().forEach { userId ->
            sessions[userId]?.forEach { session ->
                runCatching { session.send(Frame.Text(frameText)) }
            }
        }
    }
}
