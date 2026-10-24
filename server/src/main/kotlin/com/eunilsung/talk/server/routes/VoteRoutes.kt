package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.limits.UserRateLimiter
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.server.repository.VoteRepository
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.BallotRequest
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.shared.api.VoteChangeResponse
import com.eunilsung.talk.shared.api.VotesResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** 투표 — 만들기, 표 주기, 끝내기, 결과 보기. 만들기는 사람마다 횟수 한도가 있다([ServerLimits.voteCreatesPerMinute]). */
fun Route.voteRoutes(
    votes: VoteRepository,
    chats: ChatRepository,
    tokens: AuthTokenRepository,
    hub: ChatHub,
    limits: ServerLimits = ServerLimits(),
) {
    val voteCreates = UserRateLimiter(limits.voteCreatesPerMinute, limits.voteCreatesPerDay)

    /** 투표가 만들어지거나 끝났다고 알리는 대화를 방 참여자에게 보낸다. */
    suspend fun announce(change: VoteChangeResponse) {
        val roomId = change.message.roomId
        hub.send(db { chats.activeMemberIds(roomId) }, ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, change.message))
    }

    route("/rooms/{roomId}/votes") {
        get {
            val userId = call.callerUserId(tokens) ?: return@get
            val list = db { votes.votes(call.voteRoomId(), userId) } ?: return@get call.respondVoteNotFound()
            call.respond(VotesResponse(list))
        }

        post {
            val userId = call.callerUserId(tokens) ?: return@post
            val request = runCatching { call.receive<CreateVoteRequest>() }.getOrNull()?.cleaned()
            if (request == null || !request.isValid()) return@post call.respondBadVote()
            if (!voteCreates.tryAcquire(userId)) return@post call.respondTooManyRequests()
            val change = db { votes.create(call.voteRoomId(), userId, request) } ?: return@post call.respondVoteNotFound()
            announce(change)
            call.respond(change)
        }

        get("/{voteId}") {
            val userId = call.callerUserId(tokens) ?: return@get
            val vote = db { votes.vote(call.voteRoomId(), userId, call.voteId()) } ?: return@get call.respondVoteNotFound()
            call.respond(vote)
        }

        put("/{voteId}/ballot") {
            val userId = call.callerUserId(tokens) ?: return@put
            val request = runCatching { call.receive<BallotRequest>() }.getOrNull() ?: return@put call.respondBadVote()
            val vote = db { votes.cast(call.voteRoomId(), userId, call.voteId(), request.selectedIdx) }
                ?: return@put call.respondVoteNotFound()
            call.respond(vote)
        }

        post("/{voteId}/close") {
            val userId = call.callerUserId(tokens) ?: return@post
            val change = db { votes.close(call.voteRoomId(), userId, call.voteId()) } ?: return@post call.respondVoteNotFound()
            announce(change)
            call.respond(change)
        }
    }
}

/** 앞뒤 공백을 떼고 빈 항목을 버린다. */
private fun CreateVoteRequest.cleaned(): CreateVoteRequest =
    copy(title = title.trim(), items = items.map { it.trim() }.filter { it.isNotEmpty() }, endTime = endTime.trim())

/**
 * 투표 요청의 모든 칸에 길이 상한이 있어야 한다. 이 값들이 투표를 알리는 대화의 부가 정보에 그대로 실려, 방을 여는
 * 사람마다 읽힌다. 마감 시각([CreateVoteRequest.endTime])은 서버가 해석하지 않지만 날짜·시각 글자만 짧게 받는다.
 */
private fun CreateVoteRequest.isValid(): Boolean =
    title.isNotEmpty() && title.length <= MAX_VOTE_TEXT &&
        items.size in MIN_VOTE_ITEMS..MAX_VOTE_ITEMS && items.all { it.length <= MAX_VOTE_TEXT } &&
        END_TIME_PATTERN.matches(endTime)

private fun ApplicationCall.voteRoomId(): String = parameters["roomId"].orEmpty()

private fun ApplicationCall.voteId(): String = parameters["voteId"].orEmpty()

/** 방이 없는 것, 투표가 없는 것, 내가 할 수 없는 일(남의 투표 끝내기, 끝난 투표에 표 주기)을 가르지 않는다. */
private suspend fun ApplicationCall.respondVoteNotFound() =
    respond(HttpStatusCode.NotFound, ApiError(ChatErrorCode.VOTE_NOT_FOUND, "투표를 찾을 수 없습니다"))

private suspend fun ApplicationCall.respondBadVote() =
    respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "투표 요청이 올바르지 않습니다"))

private const val MIN_VOTE_ITEMS = 2
private const val MAX_VOTE_ITEMS = 20
private const val MAX_VOTE_TEXT = 200

/** 마감 시각 — 비우거나, 숫자와 `-:./+TZ`·공백으로 된 32자 이하(`2026-10-24 18:00`, ISO-8601). DB 에도 같은 길이 제약이 있다. */
private val END_TIME_PATTERN = Regex("[0-9TZ:./+ \\-]{0,32}")
