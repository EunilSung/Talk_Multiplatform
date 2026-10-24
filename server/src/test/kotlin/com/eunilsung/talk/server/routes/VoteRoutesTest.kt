package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.VoteChangeResponse
import io.ktor.client.HttpClient
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
import kotlinx.serialization.KSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 투표 경로 — 투표 알림 대화에 실리는 칸마다 길이 상한이 있고, 만드는 횟수에도 한도가 있는지 본다. */
class VoteRoutesTest {

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `마감 시각은 짧은 날짜·시각만 받고 길거나 다른 글자가 섞이면 400 이다`() = serverTest {
        val me = client.loginToken("test1")
        val room = client.authPost(me, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(listOf("test2", "test3")))
            .decode(RoomDto.serializer())

        val dated = client.createVote(me, room.id, endTime = "2026-10-24 18:00")
        val iso = client.createVote(me, room.id, endTime = "2026-10-24T18:00:00+09:00")
        val tooLong = client.createVote(me, room.id, endTime = "1".repeat(250_000))
        val text = client.createVote(me, room.id, endTime = "<b>내일</b>")

        assertEquals(HttpStatusCode.OK, dated.status)
        assertEquals("2026-10-24 18:00", dated.decode(VoteChangeResponse.serializer()).message.payload?.vote?.endTime)
        assertEquals(HttpStatusCode.OK, iso.status)
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals(HttpStatusCode.BadRequest, text.status)
    }

    @Test
    fun `투표 만들기는 사람마다 횟수 한도가 있다`() = serverTest(ServerLimits(voteCreatesPerMinute = 1)) {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val room = client.authPost(me, "/rooms", CreateRoomRequest.serializer(), CreateRoomRequest(listOf("test2", "test3")))
            .decode(RoomDto.serializer())

        val first = client.createVote(me, room.id)
        val second = client.createVote(me, room.id)
        val theirs = client.createVote(peer, room.id)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals(ApiErrorCode.TOO_MANY_REQUESTS, second.decode(ApiError.serializer()).code)
        assertEquals(HttpStatusCode.OK, theirs.status)
    }

    private suspend fun HttpClient.createVote(token: String, roomId: String, endTime: String = ""): HttpResponse =
        authPost(
            token, "/rooms/$roomId/votes", CreateVoteRequest.serializer(),
            CreateVoteRequest("회식 날짜", listOf("금요일", "토요일"), useEndTime = true, endTime = endTime),
        )

    private fun serverTest(
        limits: ServerLimits = ServerLimits(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage, limits = limits) }
        block()
    }

    private suspend fun HttpClient.loginToken(id: String): String =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, LOCAL_SEED_PASSWORD)))
        }.decode(LoginResponse.serializer()).token

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
