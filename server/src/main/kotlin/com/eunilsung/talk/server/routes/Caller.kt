package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이 요청을 보낸 사람의 아이디.
 *
 * 본문이나 쿼리에 적힌 아이디는 보지 않는다. 그 값은 얼마든지 지어낼 수 있어서, 믿는 순간 남의
 * 대화를 읽고 남인 척 말할 수 있게 된다. 신원은 오직 토큰이 정한다.
 *
 * 토큰이 없거나 모르는 값이면 401 로 답하고 null 을 돌려준다 — 호출한 쪽은 그대로 return 하면 된다.
 */
suspend fun ApplicationCall.callerUserId(tokens: AuthTokenRepository): String? {
    val userId = db { tokens.userIdOf(bearerToken()) }
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ApiError(ApiErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"))
    }
    return userId
}

/** `Authorization: Bearer` 헤더의 토큰. 없으면 빈 문자열. */
fun ApplicationCall.bearerToken(): String {
    val header = request.headers[HttpHeaders.Authorization].orEmpty()
    return if (header.startsWith(BEARER)) header.removePrefix(BEARER).trim() else ""
}

/** JDBC 는 스레드를 붙잡는다. 요청을 처리하는 스레드가 막히지 않게 IO 쪽에서 돌린다. */
suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private const val BEARER = "Bearer "
