package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MarkReadRequest
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagesResponse
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomsResponse
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.ServerEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 대화 경로 — 인증·권한·알림이 HTTP 와 WebSocket 을 거쳐 실제로 지켜지는지 본다. */
class ChatRoutesTest {

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `토큰이 없으면 방 목록을 주지 않는다`() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/rooms").status)
    }

    @Test
    fun `방을 만들고 대화를 보내면 상대의 목록에 안읽음으로 나타난다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")

        val room = client.createRoom(me, "test2")
        val sent = client.send(me, room.id, "m1", "안녕하세요")

        assertEquals(HttpStatusCode.OK, sent.status)
        val peerRoom = client.rooms(peer).single()
        assertEquals(room.id, peerRoom.id)
        assertEquals(1, peerRoom.unreadCount)
        assertEquals("안녕하세요", peerRoom.lastMessage?.content)
    }

    @Test
    fun `남의 방에는 보낼 수도 읽을 수도 없다`() = serverTest {
        val me = client.loginToken("test1")
        val outsider = client.loginToken("test3")
        val room = client.createRoom(me, "test2")
        client.send(me, room.id, "m1", "비밀")

        assertEquals(HttpStatusCode.NotFound, client.send(outsider, room.id, "m2", "끼어들기").status)
        assertEquals(HttpStatusCode.NotFound, client.authGet(outsider, "/rooms/${room.id}/messages").status)
        assertEquals(HttpStatusCode.NotFound, client.authGet(outsider, "/rooms/${room.id}").status)
    }

    @Test
    fun `앱은 초대나 퇴장 알림을 직접 보낼 수 없다`() = serverTest {
        val me = client.loginToken("test1")
        val room = client.createRoom(me, "test2")

        val response = client.authPost(
            me, "/rooms/${room.id}/messages",
            SendMessageRequest.serializer(), SendMessageRequest(id = "fake", content = "x", kind = MessageKind.EXIT),
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `빈 대화와 너무 긴 대화는 받지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val room = client.createRoom(me, "test2")

        assertEquals(HttpStatusCode.BadRequest, client.send(me, room.id, "m1", "   ").status)
        assertEquals(HttpStatusCode.BadRequest, client.send(me, room.id, "m2", "가".repeat(6001)).status)
        assertEquals(HttpStatusCode.OK, client.send(me, room.id, "m3", "가".repeat(6000)).status)
    }

    @Test
    fun `상대가 보낸 대화가 WebSocket 으로 온다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$peer") {
            client.send(me, room.id, "m1", "실시간")

            val event = nextEvent()
            assertEquals(ServerEvent.TYPE_MESSAGE, event.type)
            assertEquals(room.id, event.roomId)
            assertEquals("실시간", event.message?.content)
            assertEquals("김민준", event.message?.senderName)
        }
    }

    @Test
    fun `읽으면 보낸 사람에게 읽음 알림이 가고 안읽음 수가 내려간다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2")
        client.send(me, room.id, "m1", "읽어 주세요")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$me") {
            client.authPost(peer, "/rooms/${room.id}/read", MarkReadRequest.serializer(), MarkReadRequest("m1"))

            assertEquals(ServerEvent.TYPE_READ, nextEvent().type)
        }
        val messages = client.authGet(me, "/rooms/${room.id}/messages").decode(MessagesResponse.serializer()).messages
        assertEquals(0, messages.single().unreadCount)
    }

    @Test
    fun `참여자가 아닌 사람에게는 알림이 가지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val outsider = client.loginToken("test3")
        val room = client.createRoom(me, "test2")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$outsider") {
            client.send(me, room.id, "m1", "둘만의 대화")

            assertNull(runCatching { withTimeout(SILENCE_WAIT_MS) { nextEvent() } }.getOrNull())
        }
    }

    @Test
    fun `지어낸 토큰으로는 WebSocket 에 붙지 못한다`() = serverTest {
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=made-up") {
            assertNull(runCatching { withTimeout(EVENT_WAIT_MS) { incoming.receive() } }.getOrNull() as? Frame.Text)
        }
    }

    private suspend fun DefaultClientWebSocketSession.nextEvent(): ServerEvent = withTimeout(EVENT_WAIT_MS) {
        val frame = incoming.receive() as Frame.Text
        ServerJson.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    private fun serverTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage) }
        block()
    }

    private suspend fun HttpClient.loginToken(id: String): String =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, SeedAccounts.PASSWORD)))
        }.decode(LoginResponse.serializer()).token

    private suspend fun HttpClient.createRoom(token: String, vararg memberIds: String): RoomDto =
        authPost(token, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(memberIds.toList()))
            .decode(RoomDto.serializer())

    private suspend fun HttpClient.rooms(token: String): List<RoomDto> =
        authGet(token, "/rooms").decode(RoomsResponse.serializer()).rooms

    private suspend fun HttpClient.send(token: String, roomId: String, id: String, text: String): HttpResponse =
        authPost(token, "/rooms/$roomId/messages", SendMessageRequest.serializer(), SendMessageRequest(id, text))

    private suspend fun HttpClient.authGet(token: String, path: String): HttpResponse =
        get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun <T> HttpClient.authPost(
        token: String,
        path: String,
        serializer: KSerializer<T>,
        body: T,
    ): HttpResponse = post(path) {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(ServerJson.encodeToString(serializer, body))
    }

    private suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T =
        ServerJson.decodeFromString(serializer, bodyAsText())

    private companion object {
        const val EVENT_WAIT_MS = 5_000L
        const val SILENCE_WAIT_MS = 500L
    }
}
