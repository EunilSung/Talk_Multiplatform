package com.eunilsung.talk.server.seed

import com.eunilsung.talk.server.ServerJson
import com.eunilsung.talk.server.auth.PasswordHasher
import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.db.Database
import com.eunilsung.talk.server.local.LOCAL_SEED_PASSWORD
import com.eunilsung.talk.server.local.LocalPostgres
import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.LoginRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 시연용 계정은 비밀번호를 준 서버에서만 생기고, 그 비밀번호로 맞춰진다.
 *
 * 공용 테스트 DB 는 처음부터 계정을 채워 두므로, 여기서는 빈 DB 를 따로 띄운다.
 */
class SeedAccountsTest {

    private val users = UserRepository(emptyDatabase)

    @BeforeTest
    fun setUp() {
        emptyDatabase.connection.use { conn -> conn.createStatement().use { it.execute("TRUNCATE app_user CASCADE") } }
    }

    @Test
    fun `비밀번호를 주지 않고 띄운 서버에는 시연용 계정이 없다`() = testApplication {
        application { module(emptyDatabase, TestDatabase.fileStorage) }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(ServerJson.encodeToString(LoginRequest.serializer(), LoginRequest("test1", LOCAL_SEED_PASSWORD)))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(SeedAccounts.anyExists(users))
    }

    @Test
    fun `준 비밀번호로 계정을 만들고 다른 비밀번호로 다시 맞추면 예전 비밀번호는 통하지 않는다`() {
        SeedAccounts.ensure(users, "first-secret")
        assertTrue(PasswordHasher.verify("first-secret", storedHash("test1")))

        SeedAccounts.ensure(users, "second-secret")

        SeedAccounts.ALL.forEach { account ->
            val hash = storedHash(account.id)
            assertTrue(PasswordHasher.verify("second-secret", hash))
            assertFalse(PasswordHasher.verify("first-secret", hash))
        }
    }

    @Test
    fun `비밀번호를 다시 맞추면 예전 비밀번호로 받은 로그인이 끊기고 같은 비밀번호면 그대로다`() {
        val tokens = AuthTokenRepository(emptyDatabase)
        SeedAccounts.ensure(users, "first-secret")
        val before = tokens.issue("test1")

        SeedAccounts.ensure(users, "first-secret")
        assertEquals("test1", tokens.userIdOf(before))

        SeedAccounts.ensure(users, "second-secret")
        assertNull(tokens.userIdOf(before))
    }

    @Test
    fun `운영 설정은 켜는 값과 비밀번호가 함께 있을 때만 비밀번호를 준다`() {
        assertEquals("pw", ServerConfig.seedPasswordOf("true", "pw"))
        assertEquals("pw", ServerConfig.seedPasswordOf(" TRUE ", "pw"))
        assertNull(ServerConfig.seedPasswordOf(null, "pw"))
        assertNull(ServerConfig.seedPasswordOf("false", "pw"))
        assertNull(ServerConfig.seedPasswordOf("true", null))
        assertNull(ServerConfig.seedPasswordOf("true", " "))
    }

    private fun storedHash(userId: String): String = checkNotNull(users.findCredential(userId)).passwordHash

    private companion object {
        val emptyDatabase: DataSource by lazy {
            val postgres = LocalPostgres.startTemporary()
            Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })
            Database.connect(LocalPostgres.configOf(postgres))
        }
    }
}
