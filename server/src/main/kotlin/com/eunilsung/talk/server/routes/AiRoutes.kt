package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ai.AiAssistant
import com.eunilsung.talk.server.ai.Translation
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.TranslateRequest
import com.eunilsung.talk.shared.api.TranslationResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * 대화 번역. 요청한 사람이 볼 수 있는 대화만 번역하고, 결과는 그 사람에게만 돌려준다.
 *
 * AI 가 그 방에 초대돼 있지 않아도 된다. 번역은 방에 말을 남기지 않기 때문이다.
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
}
