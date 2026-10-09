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
 */
fun Route.pushRoutes(pushTokens: PushTokenRepository, tokens: AuthTokenRepository) {
    post("/push/token") {
        val userId = call.callerUserId(tokens) ?: return@post
        val request = runCatching { call.receive<RegisterPushTokenRequest>() }.getOrNull()
        if (request == null || request.token.isBlank() || request.token.length > MAX_TOKEN_LENGTH ||
            request.platform.isBlank() || request.platform.length > MAX_PLATFORM_LENGTH
        ) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "푸시 토큰이 올바르지 않습니다"))
        }
        db { pushTokens.register(request.token, userId, request.platform, tokens.hashOf(call.bearerToken())) }
        call.respond(HttpStatusCode.NoContent)
    }
}

private const val MAX_TOKEN_LENGTH = 4096
private const val MAX_PLATFORM_LENGTH = 20
