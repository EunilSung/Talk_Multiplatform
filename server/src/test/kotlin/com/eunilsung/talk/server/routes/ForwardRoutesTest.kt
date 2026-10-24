package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.ForwardMessageRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.MessagesResponse
import com.eunilsung.talk.shared.api.RecallRequest
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.KSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** 대화 전달 — 원본을 볼 수 있는 사람만, 받을 방의 참여자일 때만 새 대화로 보내는지 본다. */
class ForwardRoutesTest {

    private val photo = ByteArray(2048) { (it % 251).toByte() }

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `글을 전달하면 멘션 태그를 벗긴 글로 다른 방에 들어간다`() = serverTest {
        val me = client.loginToken("test1")
        val third = client.loginToken("test3")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        client.send(me, source.id, SendMessageRequest("s1", "<mention>@이서연</mention> 회의록 봐 주세요"))

        val forwarded = client.forward(me, target.id, "f1", source.id, "s1")

        assertEquals(HttpStatusCode.OK, forwarded.status)
        val message = forwarded.decode(MessageDto.serializer())
        assertEquals("f1", message.id)
        assertEquals(MessageKind.TEXT, message.kind)
        assertEquals("@이서연 회의록 봐 주세요", message.content)
        assertEquals(listOf("f1"), client.messages(third, target.id).map { it.id })
    }

    @Test
    fun `답장은 글로, 이모티콘은 이모티콘으로 전달된다`() = serverTest {
        val me = client.loginToken("test1")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        client.send(me, source.id, SendMessageRequest("s1", "원문"))
        client.send(
            me, source.id,
            SendMessageRequest("s2", "답장 본문", MessageKind.REPLY, MessagePayloadDto(replyToId = "s1", replyText = "원문")),
        )
        client.send(me, source.id, SendMessageRequest("s3", "", MessageKind.EMOTICON, MessagePayloadDto(emoticonId = "(손말_오케이)")))

        val reply = client.forward(me, target.id, "f1", source.id, "s2").decode(MessageDto.serializer())
        val emoticon = client.forward(me, target.id, "f2", source.id, "s3").decode(MessageDto.serializer())

        assertEquals(MessageKind.TEXT, reply.kind)
        assertEquals("답장 본문", reply.content)
        assertNull(reply.payload?.replyToId)
        assertEquals(MessageKind.EMOTICON, emoticon.kind)
        assertEquals("(손말_오케이)", emoticon.payload?.emoticonId)
    }

    @Test
    fun `사진을 전달하면 받을 방에 파일이 복사되고 그 방 사람만 받는다`() = serverTest {
        val me = client.loginToken("test1")
        val third = client.loginToken("test3")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        val file = client.upload(me, source.id, "여행 사진.jpg", photo).decode(FileDto.serializer())
        client.send(
            me, source.id,
            SendMessageRequest(
                "s1", "", MessageKind.IMAGE,
                MessagePayloadDto(fileId = file.id, fileName = file.name, fileSize = file.size, imageSize = "100:200"),
            ),
        )

        val message = client.forward(me, target.id, "f1", source.id, "s1").decode(MessageDto.serializer())

        val copiedId = message.payload?.fileId.orEmpty()
        assertEquals(MessageKind.IMAGE, message.kind)
        assertNotEquals(file.id, copiedId)
        assertEquals("여행 사진.jpg", message.payload?.fileName)
        assertEquals("100:200", message.payload?.imageSize)
        val downloaded = client.download(third, copiedId)
        assertEquals(HttpStatusCode.OK, downloaded.status)
        assertContentEquals(photo, downloaded.readRawBytes())
        assertEquals(HttpStatusCode.NotFound, client.download(third, file.id).status)
    }

    @Test
    fun `원본을 볼 수 없거나 받을 방에 없으면 없는 대화처럼 답한다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val third = client.loginToken("test3")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        client.send(me, source.id, SendMessageRequest("s1", "비밀"))

        val outsiderOfSource = client.forward(third, target.id, "f1", source.id, "s1")
        val outsiderOfTarget = client.forward(peer, target.id, "f2", source.id, "s1")

        assertEquals(HttpStatusCode.NotFound, outsiderOfSource.status)
        assertEquals(HttpStatusCode.NotFound, outsiderOfTarget.status)
        assertEquals(emptyList(), client.messages(me, target.id).map { it.id })
    }

    @Test
    fun `회수된 대화는 전달하지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        client.send(me, source.id, SendMessageRequest("s1", "실수"))
        client.authPost(me, "/rooms/${source.id}/recall", RecallRequest.serializer(), RecallRequest("s1"))

        assertEquals(HttpStatusCode.BadRequest, client.forward(me, target.id, "f1", source.id, "s1").status)
    }

    @Test
    fun `남이 쓴 대화 id 로 전달하면 그 대화를 돌려주지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test2", "test3")
        client.send(me, source.id, SendMessageRequest("s1", "전달할 말"))
        client.send(me, target.id, SendMessageRequest("taken", "내 말"))

        val forwarded = client.forward(peer, target.id, "taken", source.id, "s1")

        assertEquals(HttpStatusCode.Conflict, forwarded.status)
        assertFalse("내 말" in forwarded.bodyAsText())
    }

    @Test
    fun `같은 id 로 다시 전달해도 대화는 하나다`() = serverTest {
        val me = client.loginToken("test1")
        val source = client.createRoom(me, "test2")
        val target = client.createRoom(me, "test3")
        client.send(me, source.id, SendMessageRequest("s1", "한 번만"))

        val first = client.forward(me, target.id, "f1", source.id, "s1").decode(MessageDto.serializer())
        val second = client.forward(me, target.id, "f1", source.id, "s1").decode(MessageDto.serializer())

        assertEquals(first.seq, second.seq)
        assertEquals(listOf("f1"), client.messages(me, target.id).map { it.id })
    }

    private fun serverTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage) }
        block()
    }

    private suspend fun HttpClient.loginToken(id: String): String =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, LOCAL_SEED_PASSWORD)))
        }.decode(LoginResponse.serializer()).token

    private suspend fun HttpClient.createRoom(token: String, vararg memberIds: String): RoomDto =
        authPost(token, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(memberIds.toList()))
            .decode(RoomDto.serializer())

    private suspend fun HttpClient.send(token: String, roomId: String, request: SendMessageRequest): HttpResponse =
        authPost(token, "/rooms/$roomId/messages", SendMessageRequest.serializer(), request)

    private suspend fun HttpClient.forward(
        token: String,
        roomId: String,
        id: String,
        sourceRoomId: String,
        sourceMessageId: String,
    ): HttpResponse = authPost(
        token, "/rooms/$roomId/forward", ForwardMessageRequest.serializer(),
        ForwardMessageRequest(id, sourceRoomId, sourceMessageId),
    )

    private suspend fun HttpClient.messages(token: String, roomId: String): List<MessageDto> =
        get("/rooms/$roomId/messages") { header(HttpHeaders.Authorization, "Bearer $token") }
            .decode(MessagesResponse.serializer()).messages

    private suspend fun HttpClient.upload(token: String, roomId: String, name: String, bytes: ByteArray): HttpResponse =
        post("/rooms/$roomId/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("name", name)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }

    private suspend fun HttpClient.download(token: String, fileId: String): HttpResponse =
        get("/files/$fileId") { header(HttpHeaders.Authorization, "Bearer $token") }

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
}
