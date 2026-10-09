package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.ChatGroupRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatGroupsDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** 내 대화그룹 — 조회와 통째 바꾸기. 나만 보는 값이라 다른 사람에게 알리지 않는다. */
fun Route.chatGroupRoutes(groups: ChatGroupRepository, tokens: AuthTokenRepository) {
    route("/chat-groups") {
        get {
            val userId = call.callerUserId(tokens) ?: return@get
            call.respond(ChatGroupsDto(db { groups.groups(userId) }))
        }

        put {
            val userId = call.callerUserId(tokens) ?: return@put
            val request = runCatching { call.receive<ChatGroupsDto>() }.getOrNull()
            if (request == null || !request.isValid()) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "그룹 목록이 올바르지 않습니다"))
            }
            db { groups.replace(userId, request.groups) }
            call.respond(ChatGroupsDto(db { groups.groups(userId) }))
        }
    }
}

private fun ChatGroupsDto.isValid(): Boolean =
    groups.size <= MAX_GROUPS &&
        groups.map { it.id }.distinct().size == groups.size &&
        groups.all { it.id.isNotBlank() && it.id.length <= MAX_GROUP_TEXT && it.name.isNotBlank() && it.name.length <= MAX_GROUP_TEXT }

private const val MAX_GROUPS = 50
private const val MAX_GROUP_TEXT = 50
