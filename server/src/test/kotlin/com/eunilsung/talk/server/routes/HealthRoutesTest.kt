package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.module
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.HealthResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {

    @Test
    fun `DB 가 살아 있으면 ok 로 답한다`() = testApplication {
        application { module(TestDatabase.dataSource, TestDatabase.fileStorage) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString(HealthResponse.serializer(), response.bodyAsText())
        assertEquals(HealthResponse.OK, body.status)
    }

    @Test
    fun `마이그레이션이 사용자와 토큰 표를 만든다`() {
        val tables = TestDatabase.dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
                ).use { rs ->
                    buildSet { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }

        assertTrue("app_user" in tables, "app_user 표가 없다: $tables")
        assertTrue("auth_token" in tables, "auth_token 표가 없다: $tables")
    }
}
