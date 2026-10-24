package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.InviteRequest
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
import com.eunilsung.talk.shared.api.UsersResponse
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
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            client.awaitOnline(me, "test2")
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
            client.awaitOnline(me, "test1")
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
            client.awaitOnline(me, "test3")
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

    @Test
    fun `남이 보낸 대화의 id 로 보내면 409 이고 그 대화가 새지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2")
        client.send(me, room.id, "taken", "비밀 이야기")

        val response = client.send(peer, room.id, "taken", "x")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ChatErrorCode.MESSAGE_ID_TAKEN, response.decode(ApiError.serializer()).code)
        assertFalse("비밀 이야기" in response.bodyAsText())
    }

    @Test
    fun `로그아웃하면 그 토큰으로 열린 알림 연결이 닫히고 다른 기기의 연결은 남는다`() = serverTest {
        val phone = client.loginToken("test2")
        val tablet = client.loginToken("test2")
        val me = client.loginToken("test1")
        val room = client.createRoom(me, "test2")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$phone") {
            val phoneSession = this
            client.awaitOnline(me, "test2")
            socketClient.webSocket("/ws?token=$tablet") {
                client.post("/auth/logout") { header(HttpHeaders.Authorization, "Bearer $phone") }

                val reason = withTimeout(EVENT_WAIT_MS) { phoneSession.closeReason.await() }
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
                client.awaitOnline(me, "test2")
                client.send(me, room.id, "m1", "태블릿에는 온다")
                assertEquals("태블릿에는 온다", nextEvent().message?.content)
            }
        }
    }

    @Test
    fun `큰 프레임을 보내면 연결이 끊긴다`() = serverTest {
        val me = client.loginToken("test1")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$me") {
            client.awaitOnline(me, "test1")
            send(Frame.Text("x".repeat(WEBSOCKET_MAX_FRAME_BYTES + 1)))

            val reason = withTimeout(EVENT_WAIT_MS) { closeReason.await() }
            assertEquals(CloseReason.Codes.TOO_BIG.code, reason?.code)
        }
    }

    @Test
    fun `한 사람의 연결이 한도를 넘으면 가장 오래된 연결을 닫는다`() = serverTest(ServerLimits(webSocketsPerUser = 1)) {
        val me = client.loginToken("test1")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$me") {
            val oldest = this
            client.awaitOnline(me, "test1")
            socketClient.webSocket("/ws?token=$me") {
                val reason = withTimeout(EVENT_WAIT_MS) { oldest.closeReason.await() }
                assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, reason?.code)
            }
        }
    }

    @Test
    fun `단체방 만들기는 횟수 한도가 있고 1대1 방 열기는 세지 않는다`() = serverTest(ServerLimits(roomCreatesPerMinute = 1)) {
        val me = client.loginToken("test1")

        val first = client.authPost(me, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(listOf("test2", "test3")))
        val second = client.authPost(me, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(listOf("test2", "test4")))
        val direct = client.authPost(me, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(listOf("test5")))

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals(ApiErrorCode.TOO_MANY_REQUESTS, second.decode(ApiError.serializer()).code)
        assertEquals(HttpStatusCode.OK, direct.status)
    }

    @Test
    fun `방 인원 한도를 넘는 초대와 한도를 넘는 초대 횟수는 받지 않는다`() =
        serverTest(ServerLimits(maxRoomMembers = 5, invitesPerMinute = 2)) {
            val me = client.loginToken("test1")
            val room = client.createRoom(me, "test2", "test3")

            val full = client.invite(me, room.id, "test4", "test5", "test6")
            val invited = client.invite(me, room.id, "test4")
            val again = client.invite(me, room.id, "test5")

            assertEquals(HttpStatusCode.BadRequest, full.status)
            assertEquals(ChatErrorCode.ROOM_FULL, full.decode(ApiError.serializer()).code)
            assertEquals(HttpStatusCode.OK, invited.status)
            assertEquals(HttpStatusCode.TooManyRequests, again.status)
        }

    @Test
    fun `방 목록은 방마다 참여자와 내가 볼 수 있는 마지막 대화를 싣는다`() = serverTest {
        val me = client.loginToken("test1")
        val late = client.loginToken("test4")
        val group = client.createRoom(me, "test2", "test3")
        val direct = client.createRoom(me, "test4")
        client.send(me, group.id, "g1", "초대 전 말")
        client.invite(me, group.id, "test4")
        client.send(me, direct.id, "d1", "1대1 말")

        val mine = client.rooms(me).associateBy { it.id }
        val theirs = client.rooms(late).associateBy { it.id }

        assertEquals(listOf("test1", "test2", "test3", "test4"), mine.getValue(group.id).members.map { it.id }.sorted())
        assertEquals(listOf("test1", "test4"), mine.getValue(direct.id).members.map { it.id }.sorted())
        assertEquals("1대1 말", mine.getValue(direct.id).lastMessage?.content)
        assertEquals(MessageKind.INVITE, mine.getValue(group.id).lastMessage?.kind)
        assertEquals(4, theirs.getValue(group.id).members.size)
        assertEquals(MessageKind.INVITE, theirs.getValue(group.id).lastMessage?.kind)
        assertEquals("1대1 말", theirs.getValue(direct.id).lastMessage?.content)
    }

    /**
     * [userId] 의 알림 연결이 서버에 등록될 때까지 기다린다.
     *
     * 연결이 열린 직후에는 서버가 아직 토큰을 확인하는 중이다. 그 사이에 일어난 일은 알림으로 오지
     * 않으므로(앱은 연결될 때마다 다시 조회해 메운다), 등록을 확인한 뒤에 다음 단계를 진행한다.
     */
    private suspend fun HttpClient.awaitOnline(token: String, userId: String) {
        repeat(ONLINE_RETRIES) {
            val users = authGet(token, "/users").decode(UsersResponse.serializer()).users
            if (users.first { it.id == userId }.isOnline) return
            delay(ONLINE_RETRY_DELAY_MS)
        }
    }

    private suspend fun DefaultClientWebSocketSession.nextEvent(): ServerEvent = withTimeout(EVENT_WAIT_MS) {
        val frame = incoming.receive() as Frame.Text
        ServerJson.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    private fun serverTest(
        limits: ServerLimits = ServerLimits(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage, limits = limits) }
        block()
    }

    private suspend fun HttpClient.invite(token: String, roomId: String, vararg userIds: String): HttpResponse =
        authPost(token, "/rooms/$roomId/members", InviteRequest.serializer(), InviteRequest(userIds.toList()))

    private suspend fun HttpClient.loginToken(id: String): String =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, LOCAL_SEED_PASSWORD)))
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
        const val ONLINE_RETRIES = 40
        const val ONLINE_RETRY_DELAY_MS = 50L
        /** 서버의 웹소켓 프레임 한도(Application.kt 의 WEBSOCKET_MAX_FRAME_BYTES)와 같은 값. */
        const val WEBSOCKET_MAX_FRAME_BYTES = 4096
    }
}
