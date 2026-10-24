package com.eunilsung.talk.server.limits

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ReceiveRequestBytes
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.Dispatchers

/** [RequestBodyLimit] 설정. [limitOf] 가 null 을 주는 요청은 이 플러그인이 보지 않는다(스스로 크기를 세는 경로). */
class RequestBodyLimitConfig {
    var limitOf: (ApplicationCall) -> Long? = { null }
}

/**
 * 요청 본문 크기 상한.
 *
 * 길이를 밝힌 요청은 읽기 전에 거절하고, 길이를 밝히지 않은 요청(chunked)은 읽는 동안 세다가 넘으면 끊는다.
 * 본문을 다 받은 뒤에 거절하면 이미 그만큼 메모리에 올린 뒤라, 로그인 하나로도 서버 힙을 채울 수 있다.
 *
 * 넘으면 [PayloadTooLargeException] 을 던진다. 받는 쪽이 그 예외를 잡지 않으면 StatusPages 가 413 으로 답한다.
 * 알림 연결의 WebSocket 업그레이드(`GET /ws`, `Upgrade: websocket`)만 보지 않는다 — 그 뒤로 오는 것은 본문이 아니라 연결이고,
 * 프레임 크기는 WebSockets 가 따로 묶는다. `Upgrade` 헤더만 보고 빼 주면 아무 POST 에 그 헤더를 붙여 상한을 끌 수 있다.
 * Netty 는 h2c 가 꺼져 있어 POST 의 `Upgrade` 를 무시하고 보통 요청으로 처리하기 때문이다.
 */
val RequestBodyLimit = createApplicationPlugin("RequestBodyLimit", ::RequestBodyLimitConfig) {
    val limitOf = { call: ApplicationCall -> if (call.isWebSocketUpgrade()) null else pluginConfig.limitOf(call) }

    onCall { call ->
        val limit = limitOf(call) ?: return@onCall
        val length = call.request.contentLength() ?: return@onCall
        if (length > limit) throw PayloadTooLargeException(limit)
    }

    on(ReceiveRequestBytes) { call, body ->
        val limit = limitOf(call) ?: return@on body
        application.writer(Dispatchers.IO) {
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = body.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                total += read
                if (total > limit) throw PayloadTooLargeException(limit)
                channel.writeFully(buffer, 0, read)
                channel.flush()
            }
        }.channel
    }
}

/** 알림 연결을 여는 요청인지. 경로·메서드·`Upgrade` 값을 모두 본다. */
private fun ApplicationCall.isWebSocketUpgrade(): Boolean =
    request.httpMethod == HttpMethod.Get && request.path() == WEBSOCKET_PATH &&
        request.headers[HttpHeaders.Upgrade].equals(WEBSOCKET_UPGRADE, ignoreCase = true)

private const val WEBSOCKET_PATH = "/ws"
private const val WEBSOCKET_UPGRADE = "websocket"

/** 파일 올리기 경로. 본문이 파일 바이트라 크기를 그 경로가 직접 센다. */
fun ApplicationCall.isFileUpload(): Boolean =
    request.httpMethod == HttpMethod.Post && FILE_UPLOAD_PATH.matches(request.path())

private val FILE_UPLOAD_PATH = Regex("^/rooms/[^/]+/files$")

private const val COPY_BUFFER_BYTES = 8 * 1024
