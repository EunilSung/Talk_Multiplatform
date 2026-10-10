package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.shared.api.ContactGroupsDto
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.UserDto
import com.eunilsung.talk.shared.api.UsersResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 사람 경로 — 사용자 목록·프로필·접속 여부와 내그룹. */
class PeopleRoutesTest {

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `로그인한 사람은 전체 사용자를 직급 순으로 받고 비밀번호는 나가지 않는다`() = serverTest {
        val me = client.loginToken("test1")

        val response = client.authGet(me, "/users")
        val users = response.decode(UsersResponse.serializer()).users

        /** 시연용 계정 10명과 AI 계정 하나. */
        assertEquals(11, users.size)
        assertEquals("ai", users.last().id)
        assertEquals("임채원", users.first().name)
        assertEquals("부장", users.first().positionName)
        assertFalse("password" in response.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users").status)
    }

    @Test
    fun `프로필 하나를 받고 없는 사람은 404 다`() = serverTest {
        val me = client.loginToken("test1")

        val user = client.authGet(me, "/users/test7").decode(UserDto.serializer())

        assertEquals("조유진", user.name)
        assertEquals("디자인팀", user.organName)
        assertEquals("디자인 검토 요청 주세요", user.statusMessage)
        assertEquals(HttpStatusCode.NotFound, client.authGet(me, "/users/nobody").status)
    }

    @Test
    fun `알림 연결이 붙어 있는 사람만 접속 중으로 나온다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")
        val socketClient = createClient { install(WebSockets) }

        socketClient.webSocket("/ws?token=$peer") {
            /** 연결이 열린 직후에는 서버가 아직 토큰을 확인하는 중일 수 있어, 잠깐 기다리며 다시 묻는다. */
            var users = client.authGet(me, "/users").decode(UsersResponse.serializer()).users
            repeat(ONLINE_RETRIES) {
                if (users.first { it.id == "test2" }.isOnline) return@repeat
                delay(ONLINE_RETRY_DELAY_MS)
                users = client.authGet(me, "/users").decode(UsersResponse.serializer()).users
            }

            assertTrue(users.first { it.id == "test2" }.isOnline)
            assertFalse(users.first { it.id == "test3" }.isOnline)
        }
    }

    @Test
    fun `내그룹은 보낸 대로 통째로 바뀌고 사람마다 따로다`() = serverTest {
        val me = client.loginToken("test1")
        val peer = client.loginToken("test2")

        client.putGroups(me, ContactGroupDto("g1", "자주 연락", 1, listOf("test2", "test3")))
        val saved = client.putGroups(
            me,
            ContactGroupDto("g1", "프로젝트", 2, listOf("test5")),
            ContactGroupDto("g2", "점심 모임", 1, listOf("test2", "test9")),
        ).decode(ContactGroupsDto.serializer()).groups

        assertEquals(listOf("점심 모임", "프로젝트"), saved.map { it.name })
        assertEquals(listOf("test9", "test2"), saved.first().memberIds)
        assertTrue(client.authGet(peer, "/contact-groups").decode(ContactGroupsDto.serializer()).groups.isEmpty())
    }

    @Test
    fun `없는 사람과 나 자신은 그룹에 담기지 않고 잘못된 목록은 받지 않는다`() = serverTest {
        val me = client.loginToken("test1")

        val saved = client.putGroups(me, ContactGroupDto("g1", "섞인 그룹", 1, listOf("test2", "nobody", "test1")))
            .decode(ContactGroupsDto.serializer()).groups
        val duplicated = client.putGroups(me, ContactGroupDto("g1", "하나", 1), ContactGroupDto("g1", "둘", 2))
        val unnamed = client.putGroups(me, ContactGroupDto("g2", " ", 1))

        assertEquals(listOf("test2"), saved.single().memberIds)
        assertEquals(HttpStatusCode.BadRequest, duplicated.status)
        assertEquals(HttpStatusCode.BadRequest, unnamed.status)
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

    private suspend fun HttpClient.authGet(token: String, path: String): HttpResponse =
        get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun HttpClient.putGroups(token: String, vararg groups: ContactGroupDto): HttpResponse =
        put("/contact-groups") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(ContactGroupsDto.serializer(), ContactGroupsDto(groups.toList())))
        }

    private suspend fun <T> HttpResponse.decode(serializer: KSerializer<T>): T =
        ServerJson.decodeFromString(serializer, bodyAsText())

    private companion object {
        const val ONLINE_RETRIES = 20
        const val ONLINE_RETRY_DELAY_MS = 100L
    }
}
