package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MAX_FILE_BYTES
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
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
import kotlin.test.assertTrue

/** 파일 경로 — 올린 파일이 방 참여자에게만 나가고, 회수하면 사라지는지 본다. */
class FileRoutesTest {

    private val photo = ByteArray(2048) { (it % 251).toByte() }

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `올린 파일을 방 참여자가 그대로 내려받는다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2")

        val file = client.upload(me, room.id, "여행 사진.jpg", photo).decode(FileDto.serializer())
        val downloaded = client.download(peer, file.id)

        assertEquals("여행 사진.jpg", file.name)
        assertEquals(photo.size.toLong(), file.size)
        assertEquals(HttpStatusCode.OK, downloaded.status)
        assertContentEquals(photo, downloaded.readRawBytes())
    }

    @Test
    fun `방 밖의 사람은 올릴 수도 받을 수도 없다`() = serverTest {
        val me = client.loginToken("test1")
        val outsider = client.loginToken("test3")
        val room = client.createRoom(me, "test2")
        val file = client.upload(me, room.id, "a.jpg", photo).decode(FileDto.serializer())

        assertEquals(HttpStatusCode.NotFound, client.upload(outsider, room.id, "b.jpg", photo).status)
        assertEquals(HttpStatusCode.NotFound, client.download(outsider, file.id).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/files/${file.id}").status)
    }

    @Test
    fun `너무 큰 파일과 빈 파일과 이름 없는 파일은 받지 않는다`() = serverTest {
        val me = client.loginToken("test1")
        val room = client.createRoom(me, "test2")

        assertEquals(
            HttpStatusCode.PayloadTooLarge,
            client.upload(me, room.id, "big.bin", ByteArray(MAX_FILE_BYTES.toInt() + 1)).status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.upload(me, room.id, "empty.bin", ByteArray(0)).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(me, room.id, " ", photo).status)
    }

    @Test
    fun `파일 대화는 그 방에 올린 파일만 실을 수 있다`() = serverTest {
        val me = client.loginToken("test1")
        val room = client.createRoom(me, "test2")
        val otherRoom = client.createRoom(me, "test3")
        val file = client.upload(me, room.id, "a.jpg", photo).decode(FileDto.serializer())

        val sent = client.sendFile(me, room.id, "m1", file)
        val leaked = client.sendFile(me, otherRoom.id, "m2", file)
        val missing = client.sendFile(me, room.id, "m3", file.copy(id = "00000000-0000-0000-0000-000000000000"))

        assertEquals(HttpStatusCode.OK, sent.status)
        val message = sent.decode(MessageDto.serializer())
        assertEquals(MessageKind.IMAGE, message.kind)
        assertEquals(file.id, message.payload?.fileId)
        assertEquals("100:200", message.payload?.imageSize)
        assertEquals(HttpStatusCode.BadRequest, leaked.status)
        assertEquals(HttpStatusCode.BadRequest, missing.status)
    }

    @Test
    fun `파일 대화를 회수하면 파일도 더 이상 받을 수 없다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2")
        val file = client.upload(me, room.id, "a.jpg", photo).decode(FileDto.serializer())
        client.sendFile(me, room.id, "m1", file)

        val recalled = client.authPost(me, "/rooms/${room.id}/recall", RecallRequest.serializer(), RecallRequest("m1"))

        assertEquals(HttpStatusCode.OK, recalled.status)
        assertTrue(recalled.decode(MessageDto.serializer()).isRecalled)
        assertEquals(HttpStatusCode.NotFound, client.download(peer, file.id).status)
        assertEquals(HttpStatusCode.NotFound, client.download(me, file.id).status)
    }

    @Test
    fun `방에서 나간 사람은 그 방의 파일을 더 받을 수 없다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.createRoom(me, "test2", "test3")
        val file = client.upload(me, room.id, "a.jpg", photo).decode(FileDto.serializer())

        client.post("/rooms/${room.id}/leave") { header(HttpHeaders.Authorization, "Bearer $peer") }

        assertEquals(HttpStatusCode.NotFound, client.download(peer, file.id).status)
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

    private suspend fun HttpClient.upload(token: String, roomId: String, name: String, bytes: ByteArray): HttpResponse =
        post("/rooms/$roomId/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("name", name)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }

    private suspend fun HttpClient.download(token: String, fileId: String): HttpResponse =
        get("/files/$fileId") { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun HttpClient.sendFile(token: String, roomId: String, id: String, file: FileDto): HttpResponse =
        authPost(
            token, "/rooms/$roomId/messages", SendMessageRequest.serializer(),
            SendMessageRequest(
                id = id,
                kind = MessageKind.IMAGE,
                payload = MessagePayloadDto(fileId = file.id, fileName = file.name, fileSize = file.size, imageSize = "100:200"),
            ),
        )

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
