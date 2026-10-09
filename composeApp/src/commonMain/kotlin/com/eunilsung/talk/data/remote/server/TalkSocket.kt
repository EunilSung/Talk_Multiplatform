package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.Config
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

/** 서버 알림 통로. 저장소들이 이 인터페이스로 알림을 듣는다 — 테스트에서는 대역을 끼운다. */
interface ServerEvents {
    /** 서버가 보낸 알림. */
    val events: SharedFlow<ServerEvent>

    /**
     * 연결될 때마다 한 번씩 나온다. 끊겨 있던 동안의 알림은 다시 오지 않으므로, 이 신호를 받으면
     * 조회를 다시 해서 놓친 것을 메워야 한다.
     */
    val connected: SharedFlow<Unit>

    /** 연결을 유지하며 알림을 받는다. 호출한 쪽이 취소할 때까지 끝나지 않는다. */
    suspend fun listen()
}

/**
 * WebSocket 알림 수신.
 *
 * 받기만 한다 — 보내는 것은 전부 REST 다. 그래서 이 연결이 끊겨도 보낸 대화가 사라지지 않고,
 * 다시 붙을 때 조회만 다시 하면 된다. 여기서는 재연결만 신경 쓴다.
 */
class TalkSocket(
    private val httpClient: HttpClient,
    private val tokenStore: AuthTokenStore,
    private val baseUrl: String = Config.Server.BASE_URL,
) : ServerEvents {

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val _connected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val connected: SharedFlow<Unit> = _connected.asSharedFlow()

    /** 끊기면 간격을 늘려 가며 다시 붙는다. */
    override suspend fun listen() {
        var backoffMs = MIN_BACKOFF_MS
        while (true) {
            val url = baseUrl.replaceFirst("http", "ws") + "/ws?token=" + tokenStore.token().encodeURLParameter()
            val result = runCatching {
                httpClient.webSocket(url) {
                    Log.message("[Socket] 연결됨")
                    backoffMs = MIN_BACKOFF_MS
                    _connected.emit(Unit)
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        runCatching { json.decodeFromString(ServerEvent.serializer(), frame.readText()) }
                            .getOrNull()
                            ?.let { _events.emit(it) }
                    }
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            Log.message("[Socket] 끊김 — ${backoffMs}ms 뒤 다시 연결")
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val EVENT_BUFFER = 64
    }
}
