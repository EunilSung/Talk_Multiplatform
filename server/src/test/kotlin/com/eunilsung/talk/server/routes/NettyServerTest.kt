package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.RoomDto
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * 실제 Netty 로 띄워야 드러나는 것 — 요청 헤더로 본문 상한을 끄는 우회, 아주 느리게 오는 본문.
 *
 * 테스트 엔진은 `Upgrade` 같은 헤더를 붙이지 못하고 소켓 단위의 느린 전송도 흉내 내지 못한다. 그래서 루프백 포트에 서버를 띄우고
 * 소켓으로 직접 요청을 쓴다.
 */
class NettyServerTest {

    private val http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build()

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `Upgrade 헤더를 붙인 POST 도 본문 상한에 걸린다`() = withServer { port ->
        Socket(LOOPBACK, port).use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.writeText(
                "POST /auth/login HTTP/1.1\r\nHost: $LOOPBACK\r\nUpgrade: x\r\nConnection: Upgrade\r\n" +
                    "Content-Type: application/json\r\nContent-Length: $OVERSIZED_JSON_BYTES\r\n\r\n",
            )

            assertTrue(" 413 " in socket.statusLine(), "본문 상한이 꺼졌다")
        }
    }

    @Test
    fun `느리게 오는 올리기가 수십 개 있어도 다른 요청은 답을 받는다`() =
        withServer(ServerLimits(concurrentUploadsPerUser = SLOW_UPLOADS)) { port ->
            val token = login(port, "test1")
            val roomId = createRoom(port, token, "test2")
            val stalled = List(SLOW_UPLOADS) { stalledUpload(port, token, roomId) }
            try {
                Thread.sleep(SETTLE_MS)

                assertEquals(200, get(port, "/health").statusCode())
                assertEquals(200, get(port, "/rooms", token).statusCode())
            } finally {
                stalled.forEach { it.close() }
            }
        }

    @Test
    fun `한 사람이 동시에 올리는 수가 한도를 넘으면 429 이고 끝나면 자리가 돌아온다`() =
        withServer(ServerLimits(concurrentUploadsPerUser = 2)) { port ->
            val token = login(port, "test1")
            val roomId = createRoom(port, token, "test2")
            val stalled = List(2) { stalledUpload(port, token, roomId) }
            Thread.sleep(SETTLE_MS)

            val third = upload(port, token, roomId)
            stalled.forEach { it.close() }

            assertEquals(429, third.statusCode())
            assertEquals(ApiErrorCode.TOO_MANY_REQUESTS, ServerJson.decodeFromString(ApiError.serializer(), third.body()).code)
            assertEquals(200, retryUntilOk { upload(port, token, roomId) }.statusCode())
        }

    @Test
    fun `시간 안에 다 오지 않은 올리기는 408 로 끊고 자리를 돌려준다`() =
        withServer(ServerLimits(concurrentUploadsPerUser = 1, uploadTimeout = 1.seconds)) { port ->
            val token = login(port, "test1")
            val roomId = createRoom(port, token, "test2")

            val status = stalledUpload(port, token, roomId).use { it.statusLine() }

            assertTrue(" 408 " in status, "느린 올리기가 끊기지 않았다: $status")
            assertEquals(200, retryUntilOk { upload(port, token, roomId) }.statusCode())
        }

    /** 본문을 한 바이트만 보내고 멈춘 올리기. 밝힌 길이는 그보다 길다. */
    private fun stalledUpload(port: Int, token: String, roomId: String): Socket =
        Socket(LOOPBACK, port).apply {
            soTimeout = SOCKET_TIMEOUT_MS
            writeText(
                "POST /rooms/$roomId/files?name=slow.bin HTTP/1.1\r\nHost: $LOOPBACK\r\nAuthorization: Bearer $token\r\n" +
                    "Content-Type: application/octet-stream\r\nContent-Length: $SLOW_BODY_BYTES\r\n\r\nx",
            )
        }

    private fun Socket.writeText(text: String) {
        getOutputStream().write(text.toByteArray())
        getOutputStream().flush()
    }

    private fun Socket.statusLine(): String = getInputStream().bufferedReader().readLine().orEmpty()

    private fun login(port: Int, id: String): String {
        val body = ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, LOCAL_SEED_PASSWORD))
        val response = send(port, "/auth/login", null, "application/json", HttpRequest.BodyPublishers.ofString(body))
        return ServerJson.decodeFromString(LoginResponse.serializer(), response.body()).token
    }

    private fun createRoom(port: Int, token: String, vararg memberIds: String): String {
        val body = ServerJson.encodeToString(CreateRoomRequest.serializer(), CreateRoomRequest(memberIds.toList()))
        val response = send(port, "/rooms", token, "application/json", HttpRequest.BodyPublishers.ofString(body))
        return ServerJson.decodeFromString(RoomDto.serializer(), response.body()).id
    }

    private fun upload(port: Int, token: String, roomId: String): HttpResponse<String> =
        send(port, "/rooms/$roomId/files?name=a.bin", token, "application/octet-stream", HttpRequest.BodyPublishers.ofByteArray(ByteArray(16) { 1 }))

    private fun send(port: Int, path: String, token: String?, contentType: String, body: HttpRequest.BodyPublisher): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://$LOOPBACK:$port$path"))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", contentType)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .POST(body)
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(port: Int, path: String, token: String? = null): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://$LOOPBACK:$port$path"))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** 서버가 끊긴 올리기를 정리해 자리를 돌려줄 때까지 잠시 기다린다. */
    private fun retryUntilOk(request: () -> HttpResponse<String>): HttpResponse<String> {
        var last = request()
        repeat(RETRIES) {
            if (last.statusCode() == 200) return last
            Thread.sleep(RETRY_DELAY_MS)
            last = request()
        }
        return last
    }

    private fun withServer(limits: ServerLimits = ServerLimits(), block: (port: Int) -> Unit) {
        val server = embeddedServer(Netty, port = 0, host = LOOPBACK) {
            module(TestDatabase.dataSource, TestDatabase.fileStorage, limits = limits)
        }.start(wait = false)
        try {
            block(runBlocking { server.engine.resolvedConnectors().first().port })
        } finally {
            server.stop(STOP_GRACE_MS, STOP_TIMEOUT_MS)
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        /** JSON 본문 상한(256KB)보다 크다. */
        const val OVERSIZED_JSON_BYTES = 300_000
        /** IO 스레드(기본 64개)보다 많다. 올리기가 스레드를 붙잡으면 다른 요청이 답을 받지 못한다. */
        const val SLOW_UPLOADS = 80
        const val SLOW_BODY_BYTES = 1_000
        const val SETTLE_MS = 1_000L
        const val SOCKET_TIMEOUT_MS = 10_000
        const val REQUEST_TIMEOUT_SECONDS = 5L
        const val RETRIES = 20
        const val RETRY_DELAY_MS = 200L
        const val STOP_GRACE_MS = 0L
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
