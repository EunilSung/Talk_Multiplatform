package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomMemberDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.UnreadCountDto
import com.eunilsung.talk.shared.api.UserDto

/**
 * 서버 대역 — 메모리 안에서 방과 대화를 들고 실제 서버처럼 답한다.
 *
 * 로그인한 사람은 늘 [USER](`test1` / `1234`)다. [isReachable] 을 끄면 서버에 닿지 못한 상황이 되고,
 * [calls] 로는 저장소가 어떤 요청을 어떤 순서로 보냈는지 본다.
 */
class FakeTalkServer : TalkServer {

    var isReachable = true
    var isTokenValid = true
    val revoked = mutableListOf<String>()
    val calls = mutableListOf<String>()
    val readMarks = mutableListOf<Pair<String, String>>()
    /** 대화 id → 안읽음 수. 대화를 넣을 때 정해지고, 테스트가 바꿔서 "누가 읽었다"를 흉내 낸다. */
    val unreadByMessage = mutableMapOf<String, Int>()

    private var issued = 0
    private val rooms = LinkedHashMap<String, RoomDto>()
    private val messages = mutableMapOf<String, MutableList<MessageDto>>()

    /** 방을 하나 만들어 둔다. 참여자에는 늘 내가 들어간다. */
    fun addRoom(roomId: String, vararg others: Pair<String, String>, unreadCount: Int = 0): RoomDto {
        val members = listOf(RoomMemberDto(USER.id, USER.name)) + others.map { RoomMemberDto(it.first, it.second) }
        return RoomDto(id = roomId, members = members, unreadCount = unreadCount, createdAtEpochMillis = BASE_TIME)
            .also { rooms[roomId] = it }
    }

    fun removeRoom(roomId: String) {
        rooms.remove(roomId)
    }

    /** 누군가 보낸 대화를 서버에 넣는다. 돌려준 값을 알림으로 흘려보낼 수 있다. */
    fun receive(roomId: String, senderId: String, senderName: String, text: String, id: String = "$senderId-$text"): MessageDto =
        append(roomId, senderId, senderName, SendMessageRequest(id = id, content = text))

    fun messagesOf(roomId: String): List<MessageDto> = messages[roomId].orEmpty()

    override suspend fun login(id: String, password: String): ServerResult<LoginResponse> {
        if (!isReachable) return ServerResult.Unreachable
        if (id != USER.id || password != PASSWORD) {
            return ServerResult.Rejected(401, ApiErrorCode.INVALID_CREDENTIALS)
        }
        return ServerResult.Success(LoginResponse(token = "token-${++issued}", user = USER))
    }

    override suspend fun logout(token: String) {
        revoked += token
    }

    override suspend fun me(): ServerResult<UserDto> = when {
        !isReachable -> ServerResult.Unreachable
        !isTokenValid -> ServerResult.Rejected(401, ApiErrorCode.UNAUTHORIZED)
        else -> ServerResult.Success(USER)
    }

    override suspend fun rooms(): ServerResult<List<RoomDto>> = answer("rooms") {
        ServerResult.Success(rooms.values.map { it.withLastMessage() })
    }

    override suspend fun room(roomId: String): ServerResult<RoomDto> = answer("room:$roomId") {
        rooms[roomId]?.let { ServerResult.Success(it.withLastMessage()) } ?: roomNotFound()
    }

    override suspend fun createRoom(memberIds: List<String>): ServerResult<RoomDto> = answer("createRoom:$memberIds") {
        val roomId = "room-${rooms.size + 1}"
        ServerResult.Success(addRoom(roomId, *memberIds.map { it to it }.toTypedArray()))
    }

    override suspend fun invite(roomId: String, userIds: List<String>): ServerResult<RoomDto> =
        answer("invite:$roomId:$userIds") {
            val room = rooms[roomId] ?: return@answer roomNotFound()
            val updated = room.copy(members = room.members + userIds.map { RoomMemberDto(it, it) })
            rooms[roomId] = updated
            ServerResult.Success(updated)
        }

    override suspend fun leaveRoom(roomId: String): ServerResult<Unit> = answer("leave:$roomId") {
        if (rooms.remove(roomId) == null) roomNotFound() else ServerResult.Success(Unit)
    }

    override suspend fun renameRoom(roomId: String, title: String): ServerResult<Unit> = answer("rename:$roomId:$title") {
        val room = rooms[roomId] ?: return@answer roomNotFound()
        rooms[roomId] = room.copy(title = title)
        ServerResult.Success(Unit)
    }

    override suspend fun muteRoom(roomId: String, isMuted: Boolean): ServerResult<Unit> = answer("mute:$roomId:$isMuted") {
        val room = rooms[roomId] ?: return@answer roomNotFound()
        rooms[roomId] = room.copy(isMuted = isMuted)
        ServerResult.Success(Unit)
    }

    override suspend fun messages(
        roomId: String,
        afterId: String?,
        beforeId: String?,
        limit: Int,
    ): ServerResult<List<MessageDto>> = answer("messages:$roomId:after=$afterId") {
        if (roomId !in rooms) return@answer roomNotFound()
        val all = messagesOf(roomId)
        val afterSeq = all.firstOrNull { it.id == afterId }?.seq
        val page = if (afterSeq != null) all.filter { it.seq > afterSeq }.take(limit) else all.takeLast(limit)
        ServerResult.Success(page.map { it.copy(unreadCount = unreadByMessage[it.id] ?: 0) })
    }

    override suspend fun sendMessage(roomId: String, request: SendMessageRequest): ServerResult<MessageDto> =
        answer("send:$roomId:${request.id}") {
            if (roomId !in rooms) return@answer roomNotFound()
            val existing = messagesOf(roomId).firstOrNull { it.id == request.id }
            ServerResult.Success(existing ?: append(roomId, USER.id, USER.name, request))
        }

    override suspend fun markRead(roomId: String, messageId: String): ServerResult<Unit> = answer("read:$roomId:$messageId") {
        readMarks += roomId to messageId
        ServerResult.Success(Unit)
    }

    override suspend fun unreadCounts(roomId: String, fromId: String): ServerResult<List<UnreadCountDto>> =
        answer("unread:$roomId:$fromId") {
            val all = messagesOf(roomId)
            val fromSeq = all.firstOrNull { it.id == fromId }?.seq ?: return@answer ServerResult.Success(emptyList())
            ServerResult.Success(
                all.filter { it.seq >= fromSeq }.map { UnreadCountDto(it.id, unreadByMessage[it.id] ?: 0) }
            )
        }

    private fun append(roomId: String, senderId: String, senderName: String, request: SendMessageRequest): MessageDto {
        val list = messages.getOrPut(roomId) { mutableListOf() }
        val seq = list.size + 1L
        val others = (rooms[roomId]?.members?.size ?: 1) - 1
        unreadByMessage[request.id] = others
        return MessageDto(
            roomId = roomId,
            seq = seq,
            id = request.id,
            senderId = senderId,
            senderName = senderName,
            kind = request.kind,
            content = request.content,
            payload = request.payload,
            sentAtEpochMillis = BASE_TIME + seq * 1_000,
            unreadCount = others,
        ).also { list += it }
    }

    private fun RoomDto.withLastMessage(): RoomDto = copy(lastMessage = messagesOf(id).lastOrNull())

    private fun roomNotFound() = ServerResult.Rejected(404, ChatErrorCode.ROOM_NOT_FOUND)

    private inline fun <T> answer(call: String, block: () -> ServerResult<T>): ServerResult<T> {
        calls += call
        return if (isReachable) block() else ServerResult.Unreachable
    }

    companion object {
        const val PASSWORD = "1234"
        val USER = UserDto(id = "test1", name = "김민준", organName = "개발1팀", positionName = "팀장")
        /** 2026-01-01 00:00:00 UTC. 대화 시각을 이 뒤로 1초씩 벌려 순서가 흔들리지 않게 한다. */
        const val BASE_TIME = 1_767_225_600_000L
    }
}
