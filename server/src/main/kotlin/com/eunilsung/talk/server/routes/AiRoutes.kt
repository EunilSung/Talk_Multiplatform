package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ai.AiAssistant
import com.eunilsung.talk.server.ai.Polish
import com.eunilsung.talk.server.ai.Translation
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.PolishRequest
import com.eunilsung.talk.shared.api.PolishResponse
import com.eunilsung.talk.shared.api.TranslateRequest
import com.eunilsung.talk.shared.api.TranslationResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * 대화 번역과 글 다듬기. 결과는 요청한 사람에게만 돌려주고 방에는 남기지 않는다.
 *
 * 번역은 요청한 사람이 볼 수 있는 대화만 다룬다. AI 가 그 방에 초대돼 있지 않아도 된다.
 */
fun Route.aiRoutes(assistant: AiAssistant, tokens: AuthTokenRepository) {
    post("/rooms/{roomId}/translate") {
        val userId = call.callerUserId(tokens) ?: return@post
        val roomId = call.parameters["roomId"].orEmpty()
        val request = runCatching { call.receive<TranslateRequest>() }.getOrNull()
        val result = request?.let { assistant.translate(roomId, userId, it.messageId, it.targetLanguage) }
        when (result) {
            is Translation.Done -> call.respond(TranslationResponse(result.text))
            Translation.NotFound -> call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.MESSAGE_NOT_FOUND, "대화를 찾을 수 없습니다"),
            )
            Translation.Unavailable -> call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiError(ChatErrorCode.AI_UNAVAILABLE, "지금은 번역할 수 없습니다"),
            )
            Translation.BadLanguage, null -> call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ApiErrorCode.BAD_REQUEST, "요청을 읽을 수 없습니다"),
            )
        }
    }

    post("/ai/polish") {
        val userId = call.callerUserId(tokens) ?: return@post
        val request = runCatching { call.receive<PolishRequest>() }.getOrNull()
        when (val result = request?.let { assistant.polish(userId, it.text, it.style) }) {
            is Polish.Done -> call.respond(PolishResponse(result.text))
            Polish.Unavailable -> call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiError(ChatErrorCode.AI_UNAVAILABLE, "지금은 다듬을 수 없습니다"),
            )
            Polish.BadRequest, null -> call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ApiErrorCode.BAD_REQUEST, "요청을 읽을 수 없습니다"),
            )
        }
    }
}
