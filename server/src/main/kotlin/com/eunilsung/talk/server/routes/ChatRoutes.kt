package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.files.FileNames
import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.limits.UserRateLimiter
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.server.repository.FileRegistration
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.server.repository.InviteResult
import com.eunilsung.talk.server.repository.MENTION_TAG
import com.eunilsung.talk.server.repository.RoomChange
import com.eunilsung.talk.server.repository.SendResult
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.BookmarkRequest
import com.eunilsung.talk.shared.api.BookmarksResponse
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.ForwardMessageRequest
import com.eunilsung.talk.shared.api.InviteRequest
import com.eunilsung.talk.shared.api.MarkReadRequest
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.MessagesResponse
import com.eunilsung.talk.shared.api.MuteRoomRequest
import com.eunilsung.talk.shared.api.PinRoomRequest
import com.eunilsung.talk.shared.api.NoticeChangeResponse
import com.eunilsung.talk.shared.api.RecallRequest
import com.eunilsung.talk.shared.api.RenameRoomRequest
import com.eunilsung.talk.shared.api.RoomsResponse
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.shared.api.SetNoticeRequest
import com.eunilsung.talk.shared.api.ToggleReactionRequest
import com.eunilsung.talk.shared.api.UnreadCountsResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

/**
 * 대화방·대화.
 *
 * 바꾸는 요청은 전부 REST 로 받는다. WebSocket 은 "무엇이 바뀌었다"를 알리는 데만 쓴다 —
 * 연결이 끊겨도 요청이 사라지지 않고, 다시 붙은 쪽은 조회만 다시 하면 같은 상태가 된다.
 */
fun Route.chatRoutes(
    chats: ChatRepository,
    files: FileRepository,
    fileStorage: FileStorage,
    tokens: AuthTokenRepository,
    hub: ChatHub,
    limits: ServerLimits = ServerLimits(),
) {
    /** 방 만들기·초대는 한 번에 다른 사람을 끌어들인다. 한 사람이 남을 방 수천 개에 넣지 못하게 횟수를 센다. */
    val roomCreates = UserRateLimiter(limits.roomCreatesPerMinute, limits.roomCreatesPerDay)
    val invites = UserRateLimiter(limits.invitesPerMinute, limits.invitesPerDay)

    /** 방 참여자 모두에게 알린다. 방을 떠난 직후의 사람([alsoTo])에게도 보내야 할 때가 있다. */
    suspend fun notify(roomId: String, type: String, change: RoomChange? = null, alsoTo: String? = null) {
        val targets = db { chats.activeMemberIds(roomId) } + listOfNotNull(alsoTo)
        change?.message?.let { hub.send(targets, ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, it)) }
        hub.send(targets, ServerEvent(type, roomId))
    }

    /** 이미 있던 대화가 바뀌었다고(공감·회수) 방 참여자에게 알린다. */
    suspend fun notifyUpdated(message: MessageDto) {
        hub.send(
            db { chats.activeMemberIds(message.roomId) },
            ServerEvent(ServerEvent.TYPE_MESSAGE_UPDATED, message.roomId, message),
        )
    }

    /** 공지가 바뀌었다고 알린다. 그 일을 알리는 대화도 함께 보낸다. */
    suspend fun notifyNotice(change: NoticeChangeResponse) {
        val roomId = change.message.roomId
        val targets = db { chats.activeMemberIds(roomId) }
        hub.send(targets, ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, change.message))
        hub.send(targets, ServerEvent(ServerEvent.TYPE_NOTICE, roomId))
    }

    route("/rooms") {
        get {
            val userId = call.callerUserId(tokens) ?: return@get
            call.respond(RoomsResponse(db { chats.rooms(userId) }))
        }

        post {
            val userId = call.callerUserId(tokens) ?: return@post
            val request = call.receiveOrNull<CreateRoomRequest>()
            if (request == null || request.memberIds.isEmpty() || request.memberIds.size > MAX_ROOM_MEMBERS) {
                return@post call.respondBadRequest()
            }
            /** 이미 있는 1:1 방(또는 나와의 대화방)을 여는 것은 새 방이 아니라 세지 않는다. */
            val isGroup = (listOf(userId) + request.memberIds.map { it.trim().lowercase() }).distinct().size > DIRECT_ROOM_MAX_MEMBERS
            if (isGroup && !roomCreates.tryAcquire(userId)) return@post call.respondTooManyRequests()
            val change = db { chats.createRoom(userId, request.memberIds) }
                ?: return@post call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(ChatErrorCode.USER_NOT_FOUND, "없는 사용자가 있습니다"),
                )
            notify(change.roomId, ServerEvent.TYPE_ROOM, change)
            call.respondRoom(chats, change.roomId, userId)
        }

        route("/{roomId}") {
            get {
                val userId = call.callerUserId(tokens) ?: return@get
                call.respondRoom(chats, call.roomId(), userId)
            }

            post("/members") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<InviteRequest>()
                if (request == null || request.userIds.isEmpty() || request.userIds.size > MAX_ROOM_MEMBERS) {
                    return@post call.respondBadRequest()
                }
                if (!invites.tryAcquire(userId)) return@post call.respondTooManyRequests()
                when (val result = db { chats.invite(call.roomId(), userId, request.userIds) }) {
                    InviteResult.NotFound -> call.respondRoomNotFound()
                    InviteResult.RoomFull ->
                        call.respond(HttpStatusCode.BadRequest, ApiError(ChatErrorCode.ROOM_FULL, "방 인원이 너무 많습니다"))
                    is InviteResult.Invited -> {
                        notify(result.change.roomId, ServerEvent.TYPE_ROOM, result.change)
                        call.respondRoom(chats, result.change.roomId, userId)
                    }
                }
            }

            post("/leave") {
                val userId = call.callerUserId(tokens) ?: return@post
                val change = db { chats.leave(call.roomId(), userId) } ?: return@post call.respondRoomNotFound()
                notify(change.roomId, ServerEvent.TYPE_ROOM, change, alsoTo = userId)
                call.respond(HttpStatusCode.NoContent)
            }

            put("/title") {
                val userId = call.callerUserId(tokens) ?: return@put
                val title = call.receiveOrNull<RenameRoomRequest>()?.title?.trim()
                if (title.isNullOrBlank() || title.length > MAX_TITLE_LENGTH) return@put call.respondBadRequest()
                if (!db { chats.rename(call.roomId(), userId, title) }) return@put call.respondRoomNotFound()
                notify(call.roomId(), ServerEvent.TYPE_ROOM)
                call.respond(HttpStatusCode.NoContent)
            }

            put("/pin") {
                val userId = call.callerUserId(tokens) ?: return@put
                val request = call.receiveOrNull<PinRoomRequest>() ?: return@put call.respondBadRequest()
                if (!db { chats.setPinned(call.roomId(), userId, request.isPinned) }) return@put call.respondRoomNotFound()
                call.respond(HttpStatusCode.NoContent)
            }

            put("/mute") {
                val userId = call.callerUserId(tokens) ?: return@put
                val request = call.receiveOrNull<MuteRoomRequest>() ?: return@put call.respondBadRequest()
                if (!db { chats.setMuted(call.roomId(), userId, request.isMuted) }) return@put call.respondRoomNotFound()
                call.respond(HttpStatusCode.NoContent)
            }

            get("/messages") {
                val userId = call.callerUserId(tokens) ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE)
                    .coerceIn(1, MAX_PAGE_SIZE)
                val messages = db {
                    chats.messages(
                        roomId = call.roomId(),
                        userId = userId,
                        afterId = call.request.queryParameters["after"]?.takeIf { it.isNotBlank() },
                        beforeId = call.request.queryParameters["before"]?.takeIf { it.isNotBlank() },
                        limit = limit,
                    )
                } ?: return@get call.respondRoomNotFound()
                call.respond(MessagesResponse(messages))
            }

            /**
             * 대화 보내기. 부가 정보는 저장소가 종류마다 허용한 칸만으로 다시 만든다 — 파일 대화는 이 방에 내가 올린,
             * 아직 어느 대화에도 실리지 않은 파일만, 답장은 내가 볼 수 있는 원본이 있을 때만 받는다.
             */
            post("/messages") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<SendMessageRequest>()
                if (request == null || !request.isValid()) return@post call.respondBadRequest()
                val result = db {
                    chats.send(call.roomId(), userId, request.id, request.kind, request.content, request.payload)
                }
                if (result !is SendResult.Sent) return@post call.respondSendFailure(result)
                if (result.isNew) {
                    hub.send(
                        db { chats.activeMemberIds(result.message.roomId) },
                        ServerEvent(ServerEvent.TYPE_MESSAGE, result.message.roomId, result.message),
                    )
                }
                call.respond(result.message)
            }

            /**
             * 대화 전달 — 원본을 내가 볼 수 있고 이 방에 참여 중일 때만 새 대화로 넣는다. 그렇지 않으면 없는 대화처럼 답한다.
             * 첨부는 파일을 이 방으로 복사해 싣는다. 원본 방의 파일 id 를 그대로 실으면 이 방 사람에게 그 파일이 새어 나간다.
             * 복사한 파일은 내가 올린 것으로 쳐서 하루 올리기 한도에 들어간다 — 전달만 되풀이해 디스크를 채우지 못하게 한다.
             */
            post("/forward") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<ForwardMessageRequest>()
                if (request == null || !request.isValid()) return@post call.respondBadRequest()
                val roomId = call.roomId()
                /** 같은 id 로 다시 온 전달은 이미 만든 대화를 돌려준다. 그 id 가 남의 대화면 돌려주지 않는다. */
                db { chats.message(roomId, userId, request.id) }?.let { existing ->
                    return@post if (existing.senderId == userId) call.respond(existing) else call.respondSendFailure(SendResult.IdTaken)
                }
                val source = db { chats.message(request.sourceRoomId, userId, request.sourceMessageId) }
                    ?: return@post call.respondMessageNotFound()
                if (source.isRecalled || source.kind !in MessageKind.SENDABLE) return@post call.respondBadRequest()
                val payload = if (source.kind in MessageKind.WITH_FILE) {
                    val original = source.payload?.fileId?.let { db { files.find(it, userId) } }
                        ?: return@post call.respondMessageNotFound()
                    when (val copied = db { copyFileTo(roomId, userId, original, source.payload, files, fileStorage) }) {
                        is FileCopy.Copied -> copied.payload
                        FileCopy.QuotaExceeded -> return@post call.respondFileQuotaExceeded()
                        FileCopy.NotFound -> return@post call.respondMessageNotFound()
                    }
                } else {
                    source.payload?.emoticonId?.let { MessagePayloadDto(emoticonId = it) }
                }
                val kind = if (source.kind == MessageKind.REPLY) MessageKind.TEXT else source.kind
                val content = source.content.replace(MENTION_TAG, "$1")
                val result = db { chats.send(roomId, userId, request.id, kind, content, payload) }
                if (result !is SendResult.Sent) {
                    payload?.fileId?.let { copied ->
                        db { if (files.deleteUnused(copied, roomId, userId)) fileStorage.delete(copied) }
                    }
                    return@post call.respondSendFailure(result)
                }
                if (result.isNew) {
                    hub.send(
                        db { chats.activeMemberIds(result.message.roomId) },
                        ServerEvent(ServerEvent.TYPE_MESSAGE, result.message.roomId, result.message),
                    )
                }
                call.respond(result.message)
            }

            post("/reactions") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<ToggleReactionRequest>() ?: return@post call.respondBadRequest()
                val message = db { chats.toggleReaction(call.roomId(), userId, request.messageId, request.kind) }
                    ?: return@post call.respondMessageNotFound()
                notifyUpdated(message)
                call.respond(message)
            }

            post("/recall") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<RecallRequest>() ?: return@post call.respondBadRequest()
                val recalled = db { chats.recall(call.roomId(), userId, request.messageId) }
                    ?: return@post call.respondMessageNotFound()
                val message = recalled.message
                notifyUpdated(message)
                /**
                 * 회수한 대화의 파일은 지운다. 본문만 비우고 파일을 남기면 회수한 것이 아니다.
                 * 지울 파일은 대화의 부가 정보가 아니라 서버가 실을 때 확인해 둔 것이고, 이 방에 내가 올린 파일이며 다른 대화가
                 * 쓰지 않을 때만 지운다. 부가 정보를 믿으면 아무 대화에 남의 파일 id 를 적고 회수해 그 파일을 지울 수 있다.
                 */
                recalled.fileId?.let { id ->
                    db { if (files.deleteUnused(id, message.roomId, userId)) fileStorage.delete(id) }
                }
                /** 마지막 대화가 회수되면 목록의 미리보기도 바뀌어야 한다. */
                notify(message.roomId, ServerEvent.TYPE_ROOM)
                call.respond(message)
            }

            route("/notice") {
                get {
                    val userId = call.callerUserId(tokens) ?: return@get
                    val notice = db { chats.notice(call.roomId(), userId) } ?: return@get call.respondRoomNotFound()
                    call.respond(notice)
                }

                put {
                    val userId = call.callerUserId(tokens) ?: return@put
                    val content = call.receiveOrNull<SetNoticeRequest>()?.content?.trim()
                    if (content.isNullOrBlank() || content.length > MAX_CONTENT_LENGTH) return@put call.respondBadRequest()
                    val change = db { chats.setNotice(call.roomId(), userId, content) }
                        ?: return@put call.respondRoomNotFound()
                    notifyNotice(change)
                    call.respond(change)
                }

                delete {
                    val userId = call.callerUserId(tokens) ?: return@delete
                    val change = db { chats.deleteNotice(call.roomId(), userId) }
                        ?: return@delete call.respondRoomNotFound()
                    notifyNotice(change)
                    call.respond(change)
                }
            }

            /** 책갈피는 나만 보는 것이라 다른 사람에게 알리지 않는다. */
            route("/bookmarks") {
                get {
                    val userId = call.callerUserId(tokens) ?: return@get
                    val bookmarks = db { chats.bookmarks(call.roomId(), userId) }
                        ?: return@get call.respondRoomNotFound()
                    call.respond(BookmarksResponse(bookmarks))
                }

                put {
                    val userId = call.callerUserId(tokens) ?: return@put
                    val request = call.receiveOrNull<BookmarkRequest>() ?: return@put call.respondBadRequest()
                    if (!db { chats.addBookmark(call.roomId(), userId, request.messageId) }) {
                        return@put call.respondMessageNotFound()
                    }
                    call.respond(HttpStatusCode.NoContent)
                }

                delete {
                    val userId = call.callerUserId(tokens) ?: return@delete
                    val messageId = call.request.queryParameters["messageId"].orEmpty()
                    if (!db { chats.removeBookmark(call.roomId(), userId, messageId) }) {
                        return@delete call.respondRoomNotFound()
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            post("/read") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<MarkReadRequest>() ?: return@post call.respondBadRequest()
                val roomId = call.roomId()
                /** 읽은 자리가 실제로 움직였을 때만 알린다. 같은 요청이 되풀이돼도 알림이 번지지 않는다. */
                if (db { chats.markRead(roomId, userId, request.messageId) }) {
                    notify(roomId, ServerEvent.TYPE_READ)
                }
                call.respond(HttpStatusCode.NoContent)
            }

            get("/unread") {
                val userId = call.callerUserId(tokens) ?: return@get
                val from = call.request.queryParameters["from"].orEmpty()
                val counts = db { chats.unreadCounts(call.roomId(), userId, from) }
                    ?: return@get call.respondRoomNotFound()
                call.respond(UnreadCountsResponse(counts))
            }
        }
    }

    /**
     * 알림 통로. 브라우저식 WebSocket 은 헤더를 붙이기 어려워 토큰을 쿼리로 받는다.
     *
     * 이 연결로는 받기만 한다. 앱이 보낸 프레임은 읽고 버린다. 어느 로그인으로 열렸는지 함께 붙여, 그 로그인이
     * 로그아웃하면 연결도 닫힌다.
     */
    webSocket("/ws") {
        val token = call.request.queryParameters["token"].orEmpty()
        val userId = db { tokens.userIdOf(token) }
        if (userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, ApiErrorCode.UNAUTHORIZED))
            return@webSocket
        }
        hub.join(userId, tokens.hashOf(token), this)
        /** 토큰을 확인한 뒤 붙이기 전에 로그아웃했으면 그 로그아웃이 이 연결을 보지 못했다. 붙인 뒤 한 번 더 본다. */
        if (db { tokens.userIdOf(token) } == null) {
            hub.leave(userId, this)
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, ApiErrorCode.UNAUTHORIZED))
            return@webSocket
        }
        try {
            for (frame in incoming) Unit
        } finally {
            hub.leave(userId, this)
        }
    }
}

private fun SendMessageRequest.isValid(): Boolean =
    id.isNotBlank() && id.length <= MAX_ID_LENGTH &&
        kind in MessageKind.SENDABLE &&
        content.length <= MAX_CONTENT_LENGTH &&
        (content.isNotBlank() || !payload?.emoticonId.isNullOrBlank() || kind in MessageKind.WITH_FILE)

private fun ForwardMessageRequest.isValid(): Boolean =
    id.isNotBlank() && id.length <= MAX_ID_LENGTH && sourceRoomId.isNotBlank() && sourceMessageId.isNotBlank()

/** 전달할 첨부를 복사한 결과. */
private sealed interface FileCopy {
    data class Copied(val payload: MessagePayloadDto) : FileCopy

    /** 받을 방의 참여자가 아니거나 원본 바이트가 없다. */
    data object NotFound : FileCopy

    /** 복사본도 내가 올린 것으로 쳐서 하루 한도를 넘는다. */
    data object QuotaExceeded : FileCopy
}

/**
 * 내가 받을 수 있는 원본 첨부 [original] 을 [roomId] 방으로 복사해 그 파일을 가리키는 부가 정보를 만든다.
 * 바이트는 메모리에 올리지 않고 디스크에서 바로 복사한다. 하루 한도는 기록할 때 [FileRepository.register] 가 지킨다.
 */
private fun copyFileTo(
    roomId: String,
    userId: String,
    original: FileDto,
    sourcePayload: MessagePayloadDto?,
    files: FileRepository,
    fileStorage: FileStorage,
): FileCopy {
    val name = FileNames.clean(original.name).ifBlank { COPIED_FILE_NAME }
    val copied = when (val registration = files.register(roomId, userId, name, original.size)) {
        is FileRegistration.Registered -> registration.file
        FileRegistration.QuotaExceeded -> return FileCopy.QuotaExceeded
        FileRegistration.RoomNotFound -> return FileCopy.NotFound
    }
    if (!fileStorage.copy(original.id, copied.id)) {
        files.deleteUnused(copied.id, roomId, userId)
        return FileCopy.NotFound
    }
    return FileCopy.Copied(
        MessagePayloadDto(
            fileId = copied.id,
            fileName = copied.name,
            fileSize = copied.size,
            imageSize = sourcePayload?.imageSize,
        )
    )
}

internal suspend fun ApplicationCall.respondFileQuotaExceeded() =
    respond(HttpStatusCode.TooManyRequests, ApiError(ChatErrorCode.FILE_QUOTA_EXCEEDED, "오늘 올릴 수 있는 파일을 다 썼습니다"))

private fun ApplicationCall.roomId(): String = parameters["roomId"].orEmpty()

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? =
    runCatching { receive<T>() }.getOrNull()

private suspend fun ApplicationCall.respondRoom(chats: ChatRepository, roomId: String, userId: String) {
    val room = db { chats.room(roomId, userId) } ?: return respondRoomNotFound()
    respond(room)
}

private suspend fun ApplicationCall.respondRoomNotFound() =
    respond(HttpStatusCode.NotFound, ApiError(ChatErrorCode.ROOM_NOT_FOUND, "대화방을 찾을 수 없습니다"))

private suspend fun ApplicationCall.respondMessageNotFound() =
    respond(HttpStatusCode.NotFound, ApiError(ChatErrorCode.MESSAGE_NOT_FOUND, "대화를 찾을 수 없습니다"))

private suspend fun ApplicationCall.respondBadRequest() =
    respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCode.BAD_REQUEST, "요청이 올바르지 않습니다"))

internal suspend fun ApplicationCall.respondTooManyRequests() =
    respond(HttpStatusCode.TooManyRequests, ApiError(ApiErrorCode.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요"))

/** 대화를 넣지 못한 까닭대로 답한다. */
private suspend fun ApplicationCall.respondSendFailure(result: SendResult) = when (result) {
    SendResult.RoomNotFound -> respondRoomNotFound()
    SendResult.IdTaken ->
        respond(HttpStatusCode.Conflict, ApiError(ChatErrorCode.MESSAGE_ID_TAKEN, "이미 쓰인 대화 id 입니다"))
    SendResult.BadPayload, is SendResult.Sent -> respondBadRequest()
}

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200
/** 한 번에 고를 수 있는 사람 수. 방 전체 인원 한도는 [ServerLimits.maxRoomMembers] 다. */
private const val MAX_ROOM_MEMBERS = 100
/** 참여자가 이 수 이하이면 1:1 방(둘) 또는 나와의 대화방(하나)이다. */
private const val DIRECT_ROOM_MAX_MEMBERS = 2
/** 원래 이름을 정리하고 남는 것이 없을 때 복사본에 붙이는 이름. */
private const val COPIED_FILE_NAME = "file"
private const val MAX_TITLE_LENGTH = 50
private const val MAX_ID_LENGTH = 100
/** 앱 입력창의 최대 글자 수와 같다. */
private const val MAX_CONTENT_LENGTH = 6000
