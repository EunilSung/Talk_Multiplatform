package com.eunilsung.talk.server.push

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MarkReadRequest
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.MuteRoomRequest
import com.eunilsung.talk.shared.api.PushKeys
import com.eunilsung.talk.shared.api.RegisterPushTokenRequest
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.client.request.HttpRequestBuilder

/** 대화 푸시 — 누구의 어느 기기로 무엇이 나가는지, 서버 전체를 띄워 발송 대역으로 받아 본다. */
class ChatPushTest {

    /** 발송 대역. 보낸 것을 모아 두고, [gone] 에 든 토큰은 "없는 토큰"이라고 답한다. */
    private class RecordingSender : PushSender {
        val sent = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        val gone = mutableSetOf<String>()

        override suspend fun send(deviceToken: String, data: Map<String, String>): PushResult {
            sent += deviceToken to data
            return if (deviceToken in gone) PushResult.UNREGISTERED else PushResult.SENT
        }
    }

    private val sender = RecordingSender()

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `새 대화는 보낸 사람을 뺀 참여자의 기기로 간다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(me, "device-me")
        client.registerPush(peer, "device-peer")
        val room = client.createRoom(me, "test2")

        client.send(me, room.id, SendMessageRequest("m1", "<mention>@이서연</mention> 안녕하세요"))

        val (token, data) = awaitPushes(1).single()
        assertEquals("device-peer", token)
        assertEquals("@이서연 안녕하세요", data[PushKeys.MSG])
        assertEquals(room.id, data[PushKeys.MSG_KEY])
        assertEquals(PushKeys.KIND_TALK, data[PushKeys.MSG_KIND])
        assertEquals("0", data[PushKeys.MSG_TYPE])
        assertEquals("김민준", data[PushKeys.SENDER_NAME])
        assertEquals("1", data[PushKeys.UNREAD_COUNT])
    }

    @Test
    fun `배지 수는 받는 사람의 모든 방 안읽음을 더한 값이다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "device-peer")
        val direct = client.createRoom(me, "test2")
        val group = client.createRoom(me, "test2", "test3")

        client.send(me, direct.id, SendMessageRequest("d1", "하나"))
        /** 푸시는 대화 저장과 따로 나간다. 첫 푸시의 배지가 계산되기 전에 다음 대화가 들어가면 수가 달라진다. */
        awaitPushes(1)
        client.send(me, group.id, SendMessageRequest("g1", "둘"))

        val counts = awaitPushes(2).map { it.second[PushKeys.UNREAD_COUNT] }
        /** 단체방을 만들 때 생긴 초대 알림 1건이 이미 안읽음이라, 첫 푸시부터 2다. */
        assertEquals(listOf("2", "3"), counts)
    }

    @Test
    fun `알림을 끈 방과 방을 나간 사람에게는 보내지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val muted = client.loginToken("test2")
        val left = client.loginToken("test3")
        val listening = client.loginToken("test4")
        listOf(muted to "device-muted", left to "device-left", listening to "device-listening")
            .forEach { (token, device) -> client.registerPush(token, device) }
        val room = client.createRoom(me, "test2", "test3", "test4")
        client.authJson(muted, HttpMethod.Put, "/rooms/${room.id}/mute", MuteRoomRequest.serializer(), MuteRoomRequest(true))
        client.post("/rooms/${room.id}/leave") { header(HttpHeaders.Authorization, "Bearer $left") }

        client.send(me, room.id, SendMessageRequest("m1", "공지입니다"))

        assertEquals(listOf("device-listening"), awaitPushes(1).map { it.first })
    }

    @Test
    fun `로그아웃한 기기로는 더 보내지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "device-peer")
        val room = client.createRoom(me, "test2")

        client.post("/auth/logout") { header(HttpHeaders.Authorization, "Bearer $peer") }
        client.send(me, room.id, SendMessageRequest("m1", "로그아웃 뒤의 대화"))
        delay(SILENCE_WAIT_MS)

        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `같은 기기에 다른 사람이 로그인하면 토큰의 주인이 바뀐다`() = serverTest {
        val first = client.loginToken("test2")
        val second = client.loginToken("test3")
        val me = client.loginToken("test1")
        client.registerPush(first, "shared-device")
        client.registerPush(second, "shared-device")
        val roomWithFirst = client.createRoom(me, "test2")
        val roomWithSecond = client.createRoom(me, "test3")

        client.send(me, roomWithFirst.id, SendMessageRequest("a", "옛 주인에게"))
        client.send(me, roomWithSecond.id, SendMessageRequest("b", "새 주인에게"))

        assertEquals(listOf("새 주인에게"), awaitPushes(1).map { it.second[PushKeys.MSG] })
    }

    @Test
    fun `없는 토큰이라는 답을 받으면 그 토큰을 버린다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "dead-device")
        sender.gone += "dead-device"
        val room = client.createRoom(me, "test2")

        client.send(me, room.id, SendMessageRequest("m1", "첫 번째"))
        awaitPushes(1)
        delay(SILENCE_WAIT_MS)
        client.send(me, room.id, SendMessageRequest("m2", "두 번째"))
        delay(SILENCE_WAIT_MS)

        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `파일은 이름으로 이모티콘은 빈 본문으로 가고 초대와 읽음은 보내지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "device-peer")
        val room = client.createRoom(me, "test2", "test3")

        client.send(me, room.id, SendMessageRequest("e1", kind = MessageKind.EMOTICON, payload = MessagePayloadDto(emoticonId = "emo_1")))
        client.authJson(peer, HttpMethod.Post, "/rooms/${room.id}/read", MarkReadRequest.serializer(), MarkReadRequest("e1"))

        val (_, data) = awaitPushes(1).single()
        assertEquals("", data[PushKeys.MSG])
        assertEquals("32", data[PushKeys.MSG_TYPE])
    }

    @Test
    fun `토큰 없이는 등록할 수 없고 빈 토큰은 받지 않는다`() = serverTest {
        val me = client.loginToken("test1")

        assertEquals(HttpStatusCode.Unauthorized, client.registerPush("made-up", "device").status)
        assertEquals(HttpStatusCode.BadRequest, client.registerPush(me, " ").status)
        assertEquals(HttpStatusCode.NoContent, client.registerPush(me, "device").status)
    }

    @Test
    fun `같은 로그인으로 새 토큰을 등록하면 이전 토큰으로는 더 보내지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "old-device-token")
        client.registerPush(peer, "new-device-token")
        val room = client.createRoom(me, "test2")

        client.send(me, room.id, SendMessageRequest("m1", "한 번만"))
        awaitPushes(1)
        delay(SILENCE_WAIT_MS)

        assertEquals(listOf("new-device-token"), sender.sent.map { it.first })
    }

    @Test
    fun `한 사람에게 묶이는 토큰 수에는 한도가 있어 오래된 것부터 빠진다`() = serverTest(ServerLimits(pushTokensPerUser = 2)) {
        val me = client.loginToken("test1")
        val logins = List(3) { client.loginToken("test2") }
        logins.forEachIndexed { index, login ->
            client.registerPush(login, "device-$index")
            delay(REGISTER_GAP_MS)
        }
        val room = client.createRoom(me, "test2")

        client.send(me, room.id, SendMessageRequest("m1", "두 대에만"))
        awaitPushes(2)
        delay(SILENCE_WAIT_MS)

        assertEquals(setOf("device-1", "device-2"), sender.sent.map { it.first }.toSet())
    }

    @Test
    fun `토큰 모양이 아니면 받지 않는다`() = serverTest {
        val me = client.loginToken("test1")

        assertEquals(HttpStatusCode.BadRequest, client.registerPush(me, "토큰 아님").status)
        assertEquals(HttpStatusCode.BadRequest, client.registerPush(me, "a".repeat(1025)).status)
        assertEquals(HttpStatusCode.NoContent, client.registerPush(me, "fcm:APA91b-_.abc").status)
    }

    @Test
    fun `FCM 이 토큰 칸이 틀렸거나 다른 프로젝트의 토큰이라고 답하면 버린다`() {
        assertTrue(FcmSender.isGoneToken(404, ""))
        assertTrue(FcmSender.isGoneToken(400, fcmError(field = "message.token", message = "Invalid registration token")))
        assertTrue(FcmSender.isGoneToken(400, fcmError(message = "The registration token is not a valid FCM registration token")))
        assertTrue(FcmSender.isGoneToken(403, """{"error":{"details":[{"errorCode":"SENDER_ID_MISMATCH"}]}}"""))
        assertFalse(FcmSender.isGoneToken(500, "INTERNAL"))
        assertFalse(FcmSender.isGoneToken(403, "PERMISSION_DENIED"))
    }

    @Test
    fun `본문이 너무 크거나 data 칸이 틀렸다는 400 은 토큰을 버리지 않는다`() {
        assertFalse(FcmSender.isGoneToken(400, fcmError(message = "Android message is too big")))
        assertFalse(FcmSender.isGoneToken(400, fcmError(field = "message.data", message = "Invalid data payload key")))
        assertFalse(FcmSender.isGoneToken(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT"}}"""))
        assertFalse(FcmSender.isGoneToken(400, "not json"))
    }

    @Test
    fun `긴 대화도 푸시의 data 는 FCM 한도 안에 든다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        client.registerPush(peer, "device-peer")
        val room = client.createRoom(me, "test2")
        val long = "가".repeat(5990) + "😀"

        client.send(me, room.id, SendMessageRequest("long", long))

        val (_, data) = awaitPushes(1).single()
        val body = data.getValue(PushKeys.MSG)
        assertTrue(body.toByteArray().size <= 1000, "본문이 ${body.toByteArray().size}바이트다")
        assertTrue(long.startsWith(body) && body.isNotEmpty())
        val dataBytes = data.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size }
        assertTrue(dataBytes <= FCM_DATA_LIMIT_BYTES, "data 가 ${dataBytes}바이트다")
    }

    @Test
    fun `UTF-8 로 자를 때 글자 중간에서 자르지 않는다`() {
        assertEquals("ab", "abc".truncateUtf8(2))
        assertEquals("가", "가나".truncateUtf8(5))
        assertEquals("a", "a😀".truncateUtf8(4))
        assertEquals("a😀", "a😀b".truncateUtf8(5))
        assertEquals("", "가".truncateUtf8(2))
    }

    /** FCM v1 의 400 오류 본문. [field] 를 주면 `google.rpc.BadRequest` 의 위반 칸으로 싣는다. */
    private fun fcmError(field: String? = null, message: String): String {
        val details = field?.let {
            ""","details":[{"@type":"type.googleapis.com/google.rpc.BadRequest","fieldViolations":[{"field":"$it","description":"$message"}]}]"""
        }.orEmpty()
        return """{"error":{"code":400,"message":"$message","status":"INVALID_ARGUMENT"$details}}"""
    }

    private suspend fun awaitPushes(count: Int): List<Pair<String, Map<String, String>>> {
        repeat(AWAIT_RETRIES) {
            if (sender.sent.size >= count) return sender.sent.toList()
            delay(AWAIT_DELAY_MS)
        }
        return sender.sent.toList()
    }

    private fun serverTest(
        limits: ServerLimits = ServerLimits(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage, sender, limits = limits) }
        block()
    }

    private suspend fun HttpClient.loginToken(id: String): String =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, LOCAL_SEED_PASSWORD)))
        }.decode(LoginResponse.serializer()).token

    private suspend fun HttpClient.registerPush(token: String, deviceToken: String): HttpResponse =
        authJson(
            token, HttpMethod.Post, "/push/token",
            RegisterPushTokenRequest.serializer(), RegisterPushTokenRequest(deviceToken, "ANDROID"),
        )

    private suspend fun HttpClient.createRoom(token: String, vararg memberIds: String): RoomDto =
        authJson(token, HttpMethod.Post, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(memberIds.toList()))
            .decode(RoomDto.serializer())

    private suspend fun HttpClient.send(token: String, roomId: String, request: SendMessageRequest): HttpResponse =
        authJson(token, HttpMethod.Post, "/rooms/$roomId/messages", SendMessageRequest.serializer(), request)

    private suspend fun <T> HttpClient.authJson(
        token: String,
        method: HttpMethod,
        path: String,
        serializer: KSerializer<T>,
        body: T,
    ): HttpResponse {
        val build: HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(serializer, body))
        }
        return if (method == HttpMethod.Put) put(path, build) else post(path, build)
    }

    private suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T =
        ServerJson.decodeFromString(serializer, bodyAsText())

    private companion object {
        const val AWAIT_RETRIES = 50
        const val AWAIT_DELAY_MS = 50L
        const val SILENCE_WAIT_MS = 400L
        /** FCM 이 받는 data 의 최대 바이트(키와 값의 합). */
        const val FCM_DATA_LIMIT_BYTES = 4096
        /** 등록 시각이 겹치지 않게 띄운다. 한도를 넘을 때 오래된 것부터 빠지는지 보려면 순서가 분명해야 한다. */
        const val REGISTER_GAP_MS = 20L
    }
}
