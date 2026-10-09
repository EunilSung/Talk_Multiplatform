package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.shared.api.ApiErrorCode
import com.eunilsung.talk.shared.api.BookmarkDto
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.ChatGroupDto
import com.eunilsung.talk.shared.api.ContactGroupDto
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.NoticeAction
import com.eunilsung.talk.shared.api.NoticeChangeResponse
import com.eunilsung.talk.shared.api.NoticeDto
import com.eunilsung.talk.shared.api.ReactionDto
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomMemberDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.UnreadCountDto
import com.eunilsung.talk.shared.api.UserDto
import com.eunilsung.talk.shared.api.VoteChangeResponse
import com.eunilsung.talk.shared.api.VoteDto
import com.eunilsung.talk.shared.api.VoteItemDto
import com.eunilsung.talk.shared.api.VoteVoterDto

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
    private val reactionsByMessage = mutableMapOf<String, List<ReactionDto>>()
    private val recalledIds = mutableSetOf<String>()
    private val notices = mutableMapOf<String, NoticeDto>()
    private val bookmarkIds = mutableMapOf<String, MutableList<String>>()
    private var noticeCount = 0
    private val votesByRoom = mutableMapOf<String, MutableList<VoteDto>>()
    private var voteCount = 0
    private var pinCount = 0
    /** 서버의 사용자 목록과 내그룹. 테스트가 직접 바꿔 다른 기기의 변경이나 접속 상태를 흉내 낸다. */
    var people: List<UserDto> = listOf(
        USER,
        UserDto(id = "test2", name = "이서연", organName = "개발1팀", positionName = "대리", positionSort = 4),
        UserDto(id = "test3", name = "박도윤", organName = "개발2팀", positionName = "과장", positionSort = 3),
        UserDto(id = "test5", name = "정하윤", organName = "기획팀", positionName = "차장", positionSort = 1),
    )
    var contactGroups: List<ContactGroupDto> = emptyList()
    /** 서버에 저장된 내 대화그룹. 테스트가 직접 바꿔 "다른 기기에서 고쳤다"를 흉내 낸다. */
    var chatGroups: List<ChatGroupDto> = emptyList()
    /** 올라온 파일들 — 파일 id → 바이트. */
    val uploads = mutableMapOf<String, ByteArray>()

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

    /** 방의 대화 전부 — 지금의 안읽음 수·공감·회수 상태가 반영돼 있다. */
    fun messagesOf(roomId: String): List<MessageDto> = messages[roomId].orEmpty().map { it.current() }

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

    override suspend fun registerPushToken(token: String, platform: String): ServerResult<Unit> =
        answer("registerPush:$token:$platform") { ServerResult.Success(Unit) }

    override suspend fun users(): ServerResult<List<UserDto>> = answer("users") { ServerResult.Success(people) }

    override suspend fun user(userId: String): ServerResult<UserDto> = answer("user:$userId") {
        people.firstOrNull { it.id == userId }?.let { ServerResult.Success(it) }
            ?: ServerResult.Rejected(404, ChatErrorCode.USER_NOT_FOUND)
    }

    override suspend fun contactGroups(): ServerResult<List<ContactGroupDto>> = answer("contactGroups") {
        ServerResult.Success(contactGroups)
    }

    override suspend fun putContactGroups(groups: List<ContactGroupDto>): ServerResult<List<ContactGroupDto>> =
        answer("putContactGroups:${groups.map { it.name to it.memberIds }}") {
            contactGroups = groups
            ServerResult.Success(groups)
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
        val beforeSeq = all.firstOrNull { it.id == beforeId }?.seq
        val page = when {
            afterSeq != null -> all.filter { it.seq > afterSeq }.take(limit)
            beforeSeq != null -> all.filter { it.seq < beforeSeq }.takeLast(limit)
            else -> all.takeLast(limit)
        }
        ServerResult.Success(page)
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

    override suspend fun toggleReaction(roomId: String, messageId: String, kind: String): ServerResult<MessageDto> =
        answer("reaction:$roomId:$messageId:$kind") {
            if (find(roomId, messageId) == null || messageId in recalledIds) return@answer messageNotFound()
            ServerResult.Success(react(roomId, messageId, USER.id, USER.name, kind))
        }

    override suspend fun recallMessage(roomId: String, messageId: String): ServerResult<MessageDto> =
        answer("recall:$roomId:$messageId") {
            val message = find(roomId, messageId) ?: return@answer messageNotFound()
            if (message.senderId != USER.id) return@answer messageNotFound()
            ServerResult.Success(recall(roomId, messageId))
        }

    override suspend fun notice(roomId: String): ServerResult<NoticeDto> = answer("notice:$roomId") {
        if (roomId !in rooms) roomNotFound() else ServerResult.Success(notices[roomId] ?: NoticeDto(roomId = roomId))
    }

    override suspend fun setNotice(roomId: String, content: String): ServerResult<NoticeChangeResponse> =
        answer("setNotice:$roomId:$content") {
            if (roomId !in rooms) roomNotFound() else ServerResult.Success(putNotice(roomId, USER.id, USER.name, content))
        }

    override suspend fun deleteNotice(roomId: String): ServerResult<NoticeChangeResponse> = answer("deleteNotice:$roomId") {
        val previous = notices.remove(roomId) ?: return@answer roomNotFound()
        val message = append(
            roomId, USER.id, USER.name,
            SendMessageRequest("notice-${++noticeCount}", previous.content, MessageKind.NOTICE, MessagePayloadDto(noticeAction = NoticeAction.DELETE)),
        )
        ServerResult.Success(NoticeChangeResponse(NoticeDto(roomId = roomId), message))
    }

    override suspend fun bookmarks(roomId: String): ServerResult<List<BookmarkDto>> = answer("bookmarks:$roomId") {
        ServerResult.Success(
            bookmarkIds[roomId].orEmpty().mapNotNull { find(roomId, it) }.map {
                BookmarkDto(it.id, it.content, it.senderId, it.senderName, it.sentAtEpochMillis)
            }
        )
    }

    override suspend fun addBookmark(roomId: String, messageId: String): ServerResult<Unit> =
        answer("addBookmark:$roomId:$messageId") {
            if (find(roomId, messageId) == null) return@answer messageNotFound()
            bookmarkIds.getOrPut(roomId) { mutableListOf() }.apply { if (messageId !in this) add(0, messageId) }
            ServerResult.Success(Unit)
        }

    override suspend fun removeBookmark(roomId: String, messageId: String): ServerResult<Unit> =
        answer("removeBookmark:$roomId:$messageId") {
            bookmarkIds[roomId]?.remove(messageId)
            ServerResult.Success(Unit)
        }

    override suspend fun uploadFile(roomId: String, fileName: String, bytes: ByteArray): ServerResult<FileDto> =
        answer("upload:$roomId:$fileName") {
            if (roomId !in rooms) return@answer roomNotFound()
            val file = FileDto("file-${uploads.size + 1}", fileName, bytes.size.toLong())
            uploads[file.id] = bytes
            ServerResult.Success(file)
        }

    override suspend fun downloadFile(fileId: String): ServerResult<ByteArray> = answer("download:$fileId") {
        uploads[fileId]?.let { ServerResult.Success(it) } ?: ServerResult.Rejected(404, ChatErrorCode.FILE_NOT_FOUND)
    }

    /** 누군가 파일을 올리고 그 파일의 대화를 보낸다. 돌려준 값을 알림으로 흘려보낼 수 있다. */
    fun receiveFile(roomId: String, senderId: String, senderName: String, kind: String, fileName: String, bytes: ByteArray): MessageDto {
        val fileId = "file-${uploads.size + 1}"
        uploads[fileId] = bytes
        return append(
            roomId, senderId, senderName,
            SendMessageRequest(
                id = "$senderId-$fileName",
                kind = kind,
                payload = MessagePayloadDto(fileId = fileId, fileName = fileName, fileSize = bytes.size.toLong(), imageSize = "30:40"),
            ),
        )
    }

    override suspend fun pinRoom(roomId: String, isPinned: Boolean): ServerResult<Unit> = answer("pin:$roomId:$isPinned") {
        val room = rooms[roomId] ?: return@answer roomNotFound()
        rooms[roomId] = room.copy(pinnedAtEpochMillis = if (isPinned) BASE_TIME + ++pinCount else 0)
        ServerResult.Success(Unit)
    }

    override suspend fun chatGroups(): ServerResult<List<ChatGroupDto>> = answer("chatGroups") {
        ServerResult.Success(chatGroups)
    }

    override suspend fun putChatGroups(groups: List<ChatGroupDto>): ServerResult<List<ChatGroupDto>> =
        answer("putChatGroups:${groups.map { it.name }}") {
            chatGroups = groups
            ServerResult.Success(groups)
        }

    override suspend fun votes(roomId: String): ServerResult<List<VoteDto>> = answer("votes:$roomId") {
        ServerResult.Success(votesByRoom[roomId].orEmpty().reversed())
    }

    override suspend fun vote(roomId: String, voteId: String): ServerResult<VoteDto> = answer("vote:$roomId:$voteId") {
        findVote(roomId, voteId)?.let { ServerResult.Success(it) } ?: voteNotFound()
    }

    override suspend fun createVote(roomId: String, request: CreateVoteRequest): ServerResult<VoteChangeResponse> =
        answer("createVote:$roomId:${request.title}") {
            if (roomId !in rooms) return@answer roomNotFound()
            val vote = VoteDto(
                id = "vote-${++voteCount}",
                title = request.title,
                writerId = USER.id,
                multiSelect = request.multiSelect,
                allowAddItem = request.allowAddItem,
                useEndTime = request.useEndTime,
                endTime = request.endTime,
                items = request.items.mapIndexed { index, content -> VoteItemDto(index, content, 0, USER.id) },
            )
            votesByRoom.getOrPut(roomId) { mutableListOf() } += vote
            ServerResult.Success(VoteChangeResponse(vote, announceVote(roomId, vote, MessageKind.VOTE)))
        }

    override suspend fun castVote(roomId: String, voteId: String, selectedIdx: List<Int>): ServerResult<VoteDto> =
        answer("castVote:$roomId:$voteId:$selectedIdx") {
            val vote = findVote(roomId, voteId)?.takeUnless { it.isClosed } ?: return@answer voteNotFound()
            ServerResult.Success(cast(roomId, vote.id, USER.id, USER.name, selectedIdx))
        }

    override suspend fun closeVote(roomId: String, voteId: String): ServerResult<VoteChangeResponse> =
        answer("closeVote:$roomId:$voteId") {
            val vote = findVote(roomId, voteId)?.takeIf { it.writerId == USER.id && !it.isClosed }
                ?: return@answer voteNotFound()
            val closed = replaceVote(roomId, vote.copy(isClosed = true))
            ServerResult.Success(VoteChangeResponse(closed, announceVote(roomId, closed, MessageKind.VOTE_CLOSED)))
        }

    /** [userId] 의 표를 [selectedIdx] 로 바꾼다. 바뀐 투표를 돌려준다. */
    fun cast(roomId: String, voteId: String, userId: String, userName: String, selectedIdx: List<Int>): VoteDto {
        val vote = findVote(roomId, voteId)!!
        val voters = vote.voters.filterNot { it.userId == userId } + selectedIdx.map { VoteVoterDto(it, userId, userName) }
        return replaceVote(
            roomId,
            vote.copy(
                voters = voters,
                items = vote.items.map { item -> item.copy(voteCount = voters.count { it.itemIdx == item.idx }) },
            ),
        )
    }

    private fun findVote(roomId: String, voteId: String): VoteDto? = votesByRoom[roomId]?.firstOrNull { it.id == voteId }

    private fun replaceVote(roomId: String, vote: VoteDto): VoteDto {
        val list = votesByRoom.getValue(roomId)
        list[list.indexOfFirst { it.id == vote.id }] = vote
        return vote
    }

    private fun announceVote(roomId: String, vote: VoteDto, kind: String): MessageDto = append(
        roomId, USER.id, USER.name,
        SendMessageRequest("$kind-${vote.id}", vote.title, kind, MessagePayloadDto(vote = vote.copy(voters = emptyList()))),
    )

    private fun voteNotFound() = ServerResult.Rejected(404, ChatErrorCode.VOTE_NOT_FOUND)

    /** [userId] 가 공감을 누른다(같은 종류면 끄고, 다르면 갈아탄다). 바뀐 대화를 돌려준다. */
    fun react(roomId: String, messageId: String, userId: String, userName: String, kind: String): MessageDto {
        val before = reactionsByMessage[messageId].orEmpty()
        val wasSame = before.any { it.userId == userId && it.kind == kind }
        val others = before.filterNot { it.userId == userId }
        reactionsByMessage[messageId] = if (wasSame) others else others + ReactionDto(userId, userName, kind)
        return find(roomId, messageId)!!
    }

    /** 대화를 회수된 상태로 만든다. 바뀐 대화를 돌려준다. */
    fun recall(roomId: String, messageId: String): MessageDto {
        recalledIds += messageId
        return find(roomId, messageId)!!
    }

    /** [ownerId] 가 공지를 건다. 등록 알림 대화가 함께 생긴다. */
    fun putNotice(roomId: String, ownerId: String, ownerName: String, content: String): NoticeChangeResponse {
        val notice = NoticeDto(roomId, "notice-${++noticeCount}", content, ownerId, ownerName, "팀장", BASE_TIME)
        notices[roomId] = notice
        val message = append(
            roomId, ownerId, ownerName,
            SendMessageRequest("notice-msg-$noticeCount", content, MessageKind.NOTICE, MessagePayloadDto(noticeAction = NoticeAction.ADD)),
        )
        return NoticeChangeResponse(notice, message)
    }

    private fun find(roomId: String, messageId: String): MessageDto? = messagesOf(roomId).firstOrNull { it.id == messageId }

    /** 저장된 대화에 지금의 안읽음 수·공감·회수 상태를 입힌다. */
    private fun MessageDto.current(): MessageDto {
        val unread = unreadByMessage[id] ?: 0
        return if (id in recalledIds) {
            copy(content = "", payload = null, unreadCount = unread, isRecalled = true, reactions = emptyList())
        } else {
            copy(unreadCount = unread, reactions = reactionsByMessage[id].orEmpty())
        }
    }

    private fun messageNotFound() = ServerResult.Rejected(404, ChatErrorCode.MESSAGE_NOT_FOUND)

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
