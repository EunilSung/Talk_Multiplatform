package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Emoticon
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.Vote
import com.eunilsung.talk.domain.model.VoteComplete
import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteDataItem
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.domain.model.VoteSetting
import com.eunilsung.talk.domain.model.VoteVoter
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.shared.api.BookmarkDto
import com.eunilsung.talk.shared.api.FileDto
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.NoticeAction
import com.eunilsung.talk.shared.api.NoticeDto
import com.eunilsung.talk.shared.api.ReactionDto
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import com.eunilsung.talk.shared.api.VoteDto
import com.eunilsung.talk.util.ChatIdUtils
import com.eunilsung.talk.util.UserListCodec
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.util.formatFileSize
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_system_exit
import multiplatformtalk.composeapp.generated.resources.chat_system_invite
import org.jetbrains.compose.resources.getString

/**
 * 서버 모델 ↔ 도메인 모델.
 *
 * 화면과 로컬 DB 는 도메인 모델([Chat.Item], [ChatRoom.Item])만 안다. 서버가 주는 모양이 바뀌어도
 * 그 영향이 여기서 멈춘다.
 */
class ServerChatMapper(private val fileStore: ServerFileStore) {

    /**
     * 서버 대화 → 화면 대화.
     *
     * 초대·퇴장 알림은 서버가 "누가 누구를"만 알려 준다. 문구는 여기서 기기 언어에 맞춰 만든다 —
     * 화면이 본문을 그대로 그리기 때문이다.
     */
    suspend fun toChat(message: MessageDto): Chat.Item {
        val payload = message.payload
        return Chat.Item(
            chatID = message.id,
            chatType = chatTypeOf(message.kind),
            title = when (message.kind) {
                MessageKind.NOTICE -> noticeActionOf(payload?.noticeAction)
                MessageKind.VOTE, MessageKind.VOTE_CLOSED -> payload?.vote?.title ?: message.content
                else -> ""
            },
            vote = payload?.vote?.takeIf { message.kind == MessageKind.VOTE }?.let(::voteOf),
            voteComplete = payload?.vote?.takeIf { message.kind == MessageKind.VOTE_CLOSED }?.let(::voteCompleteOf),
            chatContent = when (message.kind) {
                MessageKind.INVITE -> inviteText(message.senderName, payload?.targetNames.orEmpty())
                MessageKind.EXIT -> exitText(message.senderName)
                MessageKind.VIDEO, MessageKind.FILE -> formatFileSize(payload?.fileSize ?: -1)
                else -> message.content
            },
            chatStatue = Chat.Statue.COMPLETE,
            date = chatDateOf(message.sentAtEpochMillis),
            unReadCount = message.unreadCount.toString(),
            user = User(id = message.senderId, name = message.senderName),
            replyChat = payload?.replyToId?.let { replyToId ->
                ReplyChat(
                    chatID = replyToId,
                    chatType = payload.replyKind?.let(::chatTypeOf) ?: Chat.Type.TEXT,
                    chatContent = payload.replyText.orEmpty(),
                    user = User(id = payload.replyAuthorId.orEmpty(), name = payload.replyAuthorName.orEmpty()),
                    emoticon = Emoticon(id = payload.replyEmoticonId.orEmpty()),
                )
            } ?: ReplyChat(),
            emoticon = Emoticon(id = payload?.emoticonId.orEmpty()),
            empathy = empathyOf(message.id, message.reactions),
            isRecalled = message.isRecalled,
            imagePath = localPathOf(payload),
            localPath = localPathOf(payload),
            imageSize = payload?.imageSize.orEmpty(),
            originalFileName = payload?.fileName.orEmpty(),
        )
    }

    /** 내가 쓴 대화 → 서버로 보낼 요청. 대화 id 를 그대로 실어 재전송이 겹쳐도 한 번만 들어가게 한다. */
    fun toRequest(chat: Chat.Item): SendMessageRequest {
        val reply = chat.replyChat.takeIf { it.chatID.isNotBlank() }
        val emoticonId = chat.emoticon.id.takeIf { it.isNotBlank() }
        return SendMessageRequest(
            id = chat.chatID,
            content = chat.chatContent,
            kind = messageKindOf(chat.chatType),
            payload = if (reply == null && emoticonId == null) null else MessagePayloadDto(
                emoticonId = emoticonId,
                replyToId = reply?.chatID,
                replyKind = reply?.let { messageKindOf(it.chatType) },
                replyAuthorId = reply?.user?.id,
                replyAuthorName = reply?.user?.name,
                replyText = reply?.chatContent,
                replyEmoticonId = reply?.emoticon?.id?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /** 내가 올린 파일의 대화 → 서버로 보낼 요청. 파일은 [file] 로 이미 올라가 있어야 한다. */
    fun toFileRequest(chat: Chat.Item, file: FileDto): SendMessageRequest = SendMessageRequest(
        id = chat.chatID,
        kind = messageKindOf(chat.chatType),
        payload = MessagePayloadDto(
            fileId = file.id,
            fileName = file.name,
            fileSize = file.size,
            imageSize = chat.imageSize.takeIf { it.isNotBlank() },
        ),
    )

    /** 이 대화의 파일이 기기 어디에 있는지. 파일 대화가 아니거나 아직 받지 않았으면 빈 문자열. */
    private fun localPathOf(payload: MessagePayloadDto?): String =
        payload?.fileId?.let(fileStore::localPath).orEmpty()

    /**
     * 서버 방 → 목록의 방.
     *
     * 참여자 수는 "지금까지 있었던 사람 전부"와 "나간 사람"으로 나눠 적는다. 화면이 그 둘로 1:1 방인지,
     * 상대가 떠난 방인지를 가린다.
     */
    suspend fun toRoom(room: RoomDto, myId: String): ChatRoom.Item {
        val active = room.members.filterNot { it.hasLeft }
        val left = room.members.filter { it.hasLeft }
        val last = room.lastMessage?.let { toChat(it) }
        return ChatRoom.Item(
            id = room.id,
            title = room.title.ifBlank {
                active.filterNot { it.id == myId }.ifEmpty { active }.joinToString(TITLE_SEPARATOR) { it.name }
            },
            unReadCount = room.unreadCount.toString(),
            isAlarm = if (room.isMuted) ALARM_OFF else ALARM_ON,
            lastChatDate = roomDateOf(room.lastMessage?.sentAtEpochMillis ?: room.createdAtEpochMillis),
            lastChatID = last?.chatID.orEmpty(),
            lastChatContent = last?.let { previewOf(it) }.orEmpty(),
            totalUserList = UserListCodec.encode(active.map { it.id to it.name }),
            totalUserCount = room.members.size.toString(),
            exitUserList = UserListCodec.encode(left.map { it.id to it.name }),
            exitUserCount = left.size.toString(),
            mentionCount = room.mentionCount.toString(),
            pinDate = room.pinnedAtEpochMillis.takeIf { it > 0 }?.toString().orEmpty(),
            enableMode = "0",
        )
    }

    /** 서버 공지 → 화면 공지. 공지가 없으면(내용이 비면) null. */
    fun toNotice(notice: NoticeDto): Notice? =
        if (notice.content.isBlank()) null else Notice(
            noticeId = notice.id,
            chatRoomId = notice.roomId,
            content = notice.content,
            ownerId = notice.ownerId,
            ownerName = notice.ownerName,
            ownerPosition = notice.ownerPositionName,
            date = chatDateOf(notice.createdAtEpochMillis),
        )

    /** 서버 투표 → 참여·결과 화면이 쓰는 투표. */
    fun toVoteData(vote: VoteDto): VoteData = VoteData(
        id = vote.id,
        title = vote.title,
        isClosed = vote.isClosed,
        useEndTime = vote.useEndTime,
        endTime = vote.endTime,
        participantCount = vote.voters.map { it.userId }.distinct().size,
        writeUserId = vote.writerId,
        multiSelect = vote.multiSelect,
        allowAddItem = vote.allowAddItem,
        items = vote.items.map { VoteDataItem(it.idx, it.idx, it.content, it.voteCount, it.writerId) },
        voters = vote.voters.map { VoteVoter(itemIdx = it.itemIdx, userID = it.userId, userName = it.userName) },
    )

    /** 투표가 만들어졌다는 알림 말풍선에 들어갈 내용. */
    private fun voteOf(vote: VoteDto): Vote = Vote(
        id = vote.id,
        items = vote.items.map { it.content },
        setting = VoteSetting(
            settingEndTime = vote.useEndTime,
            endTime = vote.endTime,
            multiSelect = vote.multiSelect,
            allowAddItem = vote.allowAddItem,
        ),
    )

    /** 투표가 끝났다는 알림 말풍선에 들어갈 항목별 결과. */
    private fun voteCompleteOf(vote: VoteDto): VoteComplete = VoteComplete(
        id = vote.id,
        items = vote.items.map { VoteResultItem(it.idx, it.idx, it.content, it.voteCount, it.writerId) },
    )

    fun toBookmark(roomId: String, bookmark: BookmarkDto): Bookmark = Bookmark(
        chatId = bookmark.messageId,
        chatRoomId = roomId,
        content = bookmark.content,
        date = chatDateOf(bookmark.sentAtEpochMillis),
        userId = bookmark.senderId,
        userName = bookmark.senderName,
    )

    /** 공지 알림 대화는 등록인지 삭제인지를 제목 칸에 적는다. 화면이 그 값으로 문구를 고른다. */
    private fun noticeActionOf(action: String?): String =
        if (action == NoticeAction.DELETE) Notice.ACTION_DELETE else Notice.ACTION_ADD

    /** 공감 목록 → 종류(0~5)별로 누른 사람들. */
    private fun empathyOf(chatId: String, reactions: List<ReactionDto>): EmpathyChat {
        if (reactions.isEmpty()) return EmpathyChat()
        fun usersOf(kind: String) = reactions.filter { it.kind == kind }.map { User(id = it.userId, name = it.userName) }
        return EmpathyChat(
            chatID = chatId,
            empathy0 = usersOf("0"),
            empathy1 = usersOf("1"),
            empathy2 = usersOf("2"),
            empathy3 = usersOf("3"),
            empathy4 = usersOf("4"),
            empathy5 = usersOf("5"),
        )
    }

    private fun chatTypeOf(kind: String): String = when (kind) {
        MessageKind.EMOTICON -> Chat.Type.EMOTICON
        MessageKind.REPLY -> Chat.Type.REPLY
        MessageKind.INVITE -> Chat.Type.INVITE
        MessageKind.EXIT -> Chat.Type.EXIT
        MessageKind.NOTICE -> Chat.Type.NOTICE
        MessageKind.IMAGE -> Chat.Type.IMAGE
        MessageKind.VIDEO -> Chat.Type.VIDEO
        MessageKind.FILE -> Chat.Type.FILE
        MessageKind.VOTE -> Chat.Type.VOTE
        MessageKind.VOTE_CLOSED -> Chat.Type.VOTE_COMPLETE
        else -> Chat.Type.TEXT
    }

    private fun messageKindOf(chatType: String): String = when (chatType) {
        Chat.Type.EMOTICON -> MessageKind.EMOTICON
        Chat.Type.REPLY -> MessageKind.REPLY
        Chat.Type.IMAGE -> MessageKind.IMAGE
        Chat.Type.VIDEO -> MessageKind.VIDEO
        Chat.Type.FILE -> MessageKind.FILE
        else -> MessageKind.TEXT
    }

    /** 문구 리소스를 읽을 수 없는 환경(JVM 단위 테스트)에서는 이름만 남긴다. */
    private suspend fun inviteText(actorName: String, targetNames: List<String>): String {
        val targets = targetNames.joinToString(TITLE_SEPARATOR)
        return runCatching { getString(Res.string.chat_system_invite, actorName, targets) }
            .getOrDefault("$actorName → $targets")
    }

    private suspend fun exitText(actorName: String): String =
        runCatching { getString(Res.string.chat_system_exit, actorName) }.getOrDefault(actorName)

    private suspend fun previewOf(chat: Chat.Item): String =
        runCatching { chatDisplayText(chat) }.getOrDefault(chat.chatContent)

    /** 대화 시각 — `yyyy-MM-dd HH:mm:ss:SSS`, 기기 시간대 기준. */
    private fun chatDateOf(epochMillis: Long): String =
        ChatIdUtils.formatChatDate(
            Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        )

    /** 목록의 마지막 대화 시각 — 대화 시각에서 밀리초를 뗀 `yyyy-MM-dd HH:mm:ss`. */
    private fun roomDateOf(epochMillis: Long): String =
        if (epochMillis <= 0) "" else chatDateOf(epochMillis).substringBeforeLast(':')

    private companion object {
        const val TITLE_SEPARATOR = ", "
        const val ALARM_ON = "0"
        const val ALARM_OFF = "1"
    }
}
