package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.ContactGroupRepository
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.ContactGroupsDto
import com.eunilsung.talk.shared.api.UsersResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * 사람 — 사용자 목록·프로필과 내그룹.
 *
 * 로그인한 사람만 볼 수 있다. 접속 여부는 알림 연결이 붙어 있는지로 판단하며, 응답을 만든 순간의 값이다.
 */
fun Route.peopleRoutes(
    users: UserRepository,
    groups: ContactGroupRepository,
    tokens: AuthTokenRepository,
    hub: ChatHub,
) {
    get("/users") {
        call.callerUserId(tokens) ?: return@get
        call.respond(UsersResponse(db { users.all() }.map { it.copy(isOnline = hub.isOnline(it.id)) }))
    }

    get("/users/{userId}") {
        call.callerUserId(tokens) ?: return@get
        val user = db { users.find(call.parameters["userId"].orEmpty()) }
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ApiError(ChatErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다"),
            )
        call.respond(user.copy(isOnline = hub.isOnline(user.id)))
    }

    route("/contact-groups") {
        get {
            val userId = call.callerUserId(tokens) ?: return@get
            call.respond(ContactGroupsDto(db { groups.groups(userId) }))
        }

        put {
            val userId = call.callerUserId(tokens) ?: return@put
            val request = runCatching { call.receive<ContactGroupsDto>() }.getOrNull()
            if (request == null || !request.isValid()) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "그룹 목록이 올바르지 않습니다"))
            }
            db { groups.replace(userId, request.groups) }
            call.respond(ContactGroupsDto(db { groups.groups(userId) }))
        }
    }
}

private fun ContactGroupsDto.isValid(): Boolean =
    groups.size <= MAX_CONTACT_GROUPS &&
        groups.map { it.id }.distinct().size == groups.size &&
        groups.all {
            it.id.isNotBlank() && it.id.length <= MAX_CONTACT_TEXT &&
                it.name.isNotBlank() && it.name.length <= MAX_CONTACT_TEXT &&
                it.memberIds.size <= MAX_CONTACT_MEMBERS
        }

private const val MAX_CONTACT_GROUPS = 20
private const val MAX_CONTACT_MEMBERS = 100
private const val MAX_CONTACT_TEXT = 50
