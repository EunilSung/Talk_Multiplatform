package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.UserDto
import io.ktor.client.HttpClient
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    @BeforeTest
    fun setUp() = TestDatabase.clean()

    @Test
    fun `맞는 아이디와 비밀번호면 토큰과 내 정보를 준다`() = serverTest {
        val response = client.login("test1", SeedAccounts.PASSWORD)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = ServerJson.decodeFromString(LoginResponse.serializer(), response.bodyAsText())
        assertTrue(body.token.isNotBlank())
        assertEquals("test1", body.user.id)
        assertEquals("김민준", body.user.name)
        assertEquals("개발1팀", body.user.organName)
    }

    @Test
    fun `아이디는 대소문자와 앞뒤 공백을 가리지 않는다`() = serverTest {
        val response = client.login("  TEST2 ", SeedAccounts.PASSWORD)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = ServerJson.decodeFromString(LoginResponse.serializer(), response.bodyAsText())
        assertEquals("test2", body.user.id)
    }

    @Test
    fun `비밀번호가 틀리면 401 이고 토큰을 주지 않는다`() = serverTest {
        val response = client.login("test1", "wrong")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val text = response.bodyAsText()
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, ServerJson.decodeFromString(ApiError.serializer(), text).code)
        assertFalse("token" in text)
    }

    @Test
    fun `없는 아이디도 틀린 비밀번호와 같은 응답을 받는다`() = serverTest {
        val unknownUser = client.login("nobody", SeedAccounts.PASSWORD)
        val wrongPassword = client.login("test1", "wrong")

        assertEquals(wrongPassword.status, unknownUser.status)
        assertEquals(wrongPassword.bodyAsText(), unknownUser.bodyAsText())
    }

    @Test
    fun `아이디나 비밀번호가 비면 400 이다`() = serverTest {
        assertEquals(HttpStatusCode.BadRequest, client.login("", SeedAccounts.PASSWORD).status)
        assertEquals(HttpStatusCode.BadRequest, client.login("test1", "").status)
    }

    @Test
    fun `토큰으로 내 정보를 읽는다`() = serverTest {
        val token = client.loginToken("test3")

        val response = client.get("/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("test3", ServerJson.decodeFromString(UserDto.serializer(), response.bodyAsText()).id)
    }

    @Test
    fun `토큰이 없거나 지어낸 값이면 401 이다`() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/me").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/users/me") { header(HttpHeaders.Authorization, "Bearer made-up-token") }.status,
        )
    }

    @Test
    fun `로그아웃하면 그 토큰만 못 쓰게 된다`() = serverTest {
        val phoneToken = client.loginToken("test4")
        val tabletToken = client.loginToken("test4")
        assertNotEquals(phoneToken, tabletToken)

        val logout = client.post("/auth/logout") { header(HttpHeaders.Authorization, "Bearer $phoneToken") }

        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/users/me") { header(HttpHeaders.Authorization, "Bearer $phoneToken") }.status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/users/me") { header(HttpHeaders.Authorization, "Bearer $tabletToken") }.status,
        )
    }

    @Test
    fun `DB 에는 비밀번호도 토큰도 평문으로 남지 않는다`() = serverTest {
        val token = client.loginToken("test5")

        val passwordHash = queryStrings("SELECT password_hash FROM app_user WHERE id = 'test5'").single()
        val tokenHashes = queryStrings("SELECT token_hash FROM auth_token")

        assertFalse(SeedAccounts.PASSWORD in passwordHash.split('$'))
        assertEquals(1, tokenHashes.size)
        assertFalse(token in tokenHashes)
    }

    private fun queryStrings(sql: String): List<String> =
        TestDatabase.dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }

    private fun serverTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage) }
        block()
    }

    private suspend fun HttpClient.login(id: String, password: String): HttpResponse =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest(id, password)))
        }

    private suspend fun HttpClient.loginToken(id: String): String =
        ServerJson.decodeFromString(
            LoginResponse.serializer(),
            login(id, SeedAccounts.PASSWORD).bodyAsText(),
        ).token
}
