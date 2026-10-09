package com.eunilsung.talk.server.routes

import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * 받을 준비가 됐는지.
 *
 * 프로세스가 뜬 것과 요청을 받을 수 있는 것은 다르다. DB 에 실제로 물어 보고 답한다 —
 * DB 가 죽었는데 200 을 주면 배포 스크립트가 고장 난 서버를 정상으로 본다.
 */
fun Route.healthRoutes(dataSource: DataSource) {
    get("/health") {
        val isDatabaseUp = withContext(Dispatchers.IO) {
            runCatching {
                dataSource.connection.use { conn -> conn.isValid(DB_CHECK_TIMEOUT_SECONDS) }
            }.getOrDefault(false)
        }
        if (isDatabaseUp) {
            call.respond(HealthResponse(HealthResponse.OK))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError("db_unavailable", "DB 에 연결할 수 없습니다"))
        }
    }
}

private const val DB_CHECK_TIMEOUT_SECONDS = 2
