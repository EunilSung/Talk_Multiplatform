package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.ai.AiAssistant
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.SummaryRequest
import com.eunilsung.talk.shared.api.SummaryResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * 대화 요약. 요청한 사람이 볼 수 있는 대화만 요약하고, 결과는 그 사람에게만 돌려준다.
 *
 * AI 가 그 방에 초대돼 있지 않아도 된다. 요약은 방에 말을 남기지 않기 때문이다.
 */
fun Route.aiRoutes(assistant: AiAssistant, chats: ChatRepository, tokens: AuthTokenRepository) {
    post("/rooms/{roomId}/summary") {
        val userId = call.callerUserId(tokens) ?: return@post
        val roomId = call.parameters["roomId"].orEmpty()
        val request = runCatching { call.receive<SummaryRequest>() }.getOrDefault(SummaryRequest())
        if (db { chats.room(roomId, userId) } == null) {
            return@post call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.ROOM_NOT_FOUND, "대화방을 찾을 수 없습니다"),
            )
        }
        val summary = assistant.summarize(roomId, userId, request.afterMessageId?.takeIf { it.isNotBlank() })
            ?: return@post call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiError(ChatErrorCode.AI_UNAVAILABLE, "지금은 요약할 수 없습니다"),
            )
        call.respond(SummaryResponse(summary))
    }
}
