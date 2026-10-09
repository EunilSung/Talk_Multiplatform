package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.auth.PasswordHasher
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** 로그인·로그아웃·내 정보. */
fun Route.authRoutes(users: UserRepository, tokens: AuthTokenRepository) {

    /**
     * 로그인. 아이디가 없는 것과 비밀번호가 틀린 것을 같은 응답으로 돌려준다 —
     * 구분해 주면 어떤 아이디가 있는지 밖에서 알아낼 수 있다.
     */
    post("/auth/login") {
        val request = runCatching { call.receive<LoginRequest>() }.getOrNull()
        if (request == null || request.id.isBlank() || request.password.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "아이디와 비밀번호가 필요합니다"))
            return@post
        }

        val response = db {
            val credential = users.findCredential(request.id)
            /**
             * 없는 아이디여도 해시 검증을 한 번 돌린다. 건너뛰면 응답이 눈에 띄게 빨라져서,
             * 걸린 시간만으로 아이디가 있는지 알아낼 수 있다.
             */
            val isValid = PasswordHasher.verify(request.password, credential?.passwordHash ?: UNKNOWN_USER_HASH)
            if (credential != null && isValid) {
                LoginResponse(token = tokens.issue(credential.user.id), user = credential.user)
            } else {
                null
            }
        }

        if (response == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(ApiErrorCode.INVALID_CREDENTIALS, "아이디 또는 비밀번호가 올바르지 않습니다"),
            )
        } else {
            call.respond(response)
        }
    }

    /** 로그아웃 — 이 기기의 토큰만 없앤다. 이미 없는 토큰이어도 성공으로 답한다. */
    post("/auth/logout") {
        val token = call.bearerToken()
        db { tokens.revoke(token) }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/users/me") {
        val userId = call.callerUserId(tokens) ?: return@get
        val user = db { users.find(userId) }
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiError(ApiErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"))
        } else {
            call.respond(user)
        }
    }
}

/** 없는 아이디를 검증할 때 쓰는 자리 채움 해시. 어떤 비밀번호와도 맞지 않는다. */
private val UNKNOWN_USER_HASH: String by lazy { PasswordHasher.hash("unknown-user-placeholder") }
