package com.eunilsung.talk.server.routes

import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.server.repository.RoomChange
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.BookmarkRequest
import com.eunilsung.talk.shared.api.BookmarksResponse
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.CreateRoomRequest
import com.eunilsung.talk.shared.api.InviteRequest
import com.eunilsung.talk.shared.api.MarkReadRequest
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagesResponse
import com.eunilsung.talk.shared.api.MuteRoomRequest
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
) {

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
                val change = db { chats.invite(call.roomId(), userId, request.userIds) }
                    ?: return@post call.respondRoomNotFound()
                notify(change.roomId, ServerEvent.TYPE_ROOM, change)
                call.respondRoom(chats, change.roomId, userId)
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

            post("/messages") {
                val userId = call.callerUserId(tokens) ?: return@post
                val request = call.receiveOrNull<SendMessageRequest>()
                if (request == null || !request.isValid()) return@post call.respondBadRequest()
                /** 파일 대화는 이 방에 올라온 파일만 가리킬 수 있다. 남의 방 파일 id 를 끼워 넣으면 그 파일이 새어 나간다. */
                if (request.kind in MessageKind.WITH_FILE &&
                    !db { files.belongsTo(request.payload?.fileId.orEmpty(), call.roomId()) }
                ) {
                    return@post call.respondBadRequest()
                }
                val message = db {
                    chats.send(call.roomId(), userId, request.id, request.kind, request.content, request.payload)
                } ?: return@post call.respondRoomNotFound()
                hub.send(
                    db { chats.activeMemberIds(message.roomId) },
                    ServerEvent(ServerEvent.TYPE_MESSAGE, message.roomId, message),
                )
                call.respond(message)
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
                val fileId = db { chats.fileIdOf(call.roomId(), request.messageId) }
                val message = db { chats.recall(call.roomId(), userId, request.messageId) }
                    ?: return@post call.respondMessageNotFound()
                notifyUpdated(message)
                /** 회수한 대화의 파일은 지운다. 본문만 비우고 파일을 남기면 회수한 것이 아니다. */
                fileId?.let { id ->
                    db {
                        files.delete(id)
                        fileStorage.delete(id)
                    }
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
     * 이 연결로는 받기만 한다. 앱이 보낸 프레임은 읽고 버린다.
     */
    webSocket("/ws") {
        val token = call.request.queryParameters["token"].orEmpty()
        val userId = db { tokens.userIdOf(token) }
        if (userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, ApiErrorCode.UNAUTHORIZED))
            return@webSocket
        }
        hub.join(userId, this)
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

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200
private const val MAX_ROOM_MEMBERS = 100
private const val MAX_TITLE_LENGTH = 50
private const val MAX_ID_LENGTH = 100
/** 앱 입력창의 최대 글자 수와 같다. */
private const val MAX_CONTENT_LENGTH = 6000
