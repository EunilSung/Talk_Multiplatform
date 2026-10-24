package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.PushTokenRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.RegisterPushTokenRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * 이 기기의 푸시 토큰 등록.
 *
 * 지우는 경로는 따로 없다. 토큰은 등록한 로그인에 묶여 있어, 로그아웃하면 함께 지워진다.
 * 로그인 하나에 토큰 하나다 — 같은 로그인으로 새 토큰을 등록하면 이전 것을 바꾼다. 한 사람에게 묶이는 토큰 수에도
 * 한도([maxPerUser])가 있다. 가짜 토큰을 잔뜩 등록해 대화 한 건을 수천 번의 발송으로 불리지 못하게 한다.
 */
fun Route.pushRoutes(pushTokens: PushTokenRepository, tokens: AuthTokenRepository, maxPerUser: Int) {
    post("/push/token") {
        val userId = call.callerUserId(tokens) ?: return@post
        val request = runCatching { call.receive<RegisterPushTokenRequest>() }.getOrNull()
        if (request == null || !TOKEN_PATTERN.matches(request.token) ||
            request.platform.isBlank() || request.platform.length > MAX_PLATFORM_LENGTH
        ) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "푸시 토큰이 올바르지 않습니다"))
        }
        db { pushTokens.register(request.token, userId, request.platform, tokens.hashOf(call.bearerToken()), maxPerUser) }
        call.respond(HttpStatusCode.NoContent)
    }
}

/** FCM 등록 토큰의 모양 — 영문·숫자와 `_`·`-`·`:`·`.` 만, 1024자까지. 실제 토큰은 200자 안팎이다. */
private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_:.\\-]{1,1024}$")
private const val MAX_PLATFORM_LENGTH = 20
